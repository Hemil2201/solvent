import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db import get_db
from app.models.plaid_account import PlaidAccount
from app.models.plaid_item import PlaidItem
from app.models.user import User
from app.schemas.plaid import ExchangePublicTokenRequest, LinkTokenResponse, PlaidAccountOut
from app.services import plaid_client
from app.services.token_encryption import decrypt_token, encrypt_token

router = APIRouter(prefix="/plaid", tags=["plaid"])


def _to_out(account: PlaidAccount, institution_name: str | None) -> PlaidAccountOut:
    return PlaidAccountOut(
        id=account.id,
        item_id=account.plaid_item_id,
        name=account.name,
        official_name=account.official_name,
        mask=account.mask,
        type=account.type,
        subtype=account.subtype,
        current_balance=account.current_balance,
        available_balance=account.available_balance,
        iso_currency_code=account.iso_currency_code,
        institution_name=institution_name,
    )


@router.post("/link-token", response_model=LinkTokenResponse)
def create_link_token(current_user: User = Depends(get_current_user)) -> LinkTokenResponse:
    link_token = plaid_client.create_link_token(user_id=str(current_user.id))
    return LinkTokenResponse(link_token=link_token)


@router.post("/exchange-public-token", response_model=list[PlaidAccountOut])
def exchange_public_token(
    body: ExchangePublicTokenRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[PlaidAccountOut]:
    access_token, item_id = plaid_client.exchange_public_token(body.public_token)
    now = datetime.now(timezone.utc)

    item = PlaidItem(
        id=uuid.uuid4(),
        user_id=current_user.id,
        plaid_item_id=item_id,
        plaid_institution_id=body.institution_id,
        institution_name=body.institution_name,
        access_token_encrypted=encrypt_token(access_token),
        status="active",
        created_at=now,
        updated_at=now,
    )
    db.add(item)
    db.flush()

    accounts = []
    for b in plaid_client.get_balances(access_token):
        account = PlaidAccount(
            id=uuid.uuid4(),
            plaid_item_id=item.id,
            plaid_account_id=b["account_id"],
            name=b["name"],
            official_name=b["official_name"],
            mask=b["mask"],
            type=b["type"],
            subtype=b["subtype"],
            current_balance=b["current_balance"],
            available_balance=b["available_balance"],
            iso_currency_code=b["iso_currency_code"],
            updated_at=now,
        )
        db.add(account)
        accounts.append(account)

    db.commit()
    for a in accounts:
        db.refresh(a)
    return [_to_out(a, item.institution_name) for a in accounts]


@router.get("/accounts", response_model=list[PlaidAccountOut])
def list_accounts(db: Session = Depends(get_db), current_user: User = Depends(get_current_user)) -> list[PlaidAccountOut]:
    # Private by design: only this user's own items, ever — no "shared"
    # visibility into a partner's linked accounts.
    items = list(
        db.execute(
            select(PlaidItem).where(PlaidItem.user_id == current_user.id, PlaidItem.status == "active")
        ).scalars()
    )

    results: list[PlaidAccountOut] = []
    now = datetime.now(timezone.utc)
    for item in items:
        existing_by_plaid_id = {
            a.plaid_account_id: a
            for a in db.execute(select(PlaidAccount).where(PlaidAccount.plaid_item_id == item.id)).scalars()
        }
        try:
            balances = plaid_client.get_balances(decrypt_token(item.access_token_encrypted))
        except Exception:
            # Refresh failed (e.g. item needs re-auth) — fall back to the
            # last-known stored balances rather than dropping this item's
            # accounts from the list entirely.
            results.extend(_to_out(a, item.institution_name) for a in existing_by_plaid_id.values())
            continue

        for b in balances:
            account = existing_by_plaid_id.get(b["account_id"])
            if account is None:
                account = PlaidAccount(id=uuid.uuid4(), plaid_item_id=item.id, plaid_account_id=b["account_id"])
                db.add(account)
            account.name = b["name"]
            account.official_name = b["official_name"]
            account.mask = b["mask"]
            account.type = b["type"]
            account.subtype = b["subtype"]
            account.current_balance = b["current_balance"]
            account.available_balance = b["available_balance"]
            account.iso_currency_code = b["iso_currency_code"]
            account.updated_at = now
            results.append(_to_out(account, item.institution_name))

    db.commit()
    return results


@router.delete("/items/{item_id}", status_code=204)
def unlink_item(
    item_id: uuid.UUID, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)
) -> None:
    item = db.execute(
        select(PlaidItem).where(PlaidItem.id == item_id, PlaidItem.user_id == current_user.id)
    ).scalar_one_or_none()
    if item is None:
        raise HTTPException(status_code=404, detail="Linked account not found")
    try:
        plaid_client.remove_item(decrypt_token(item.access_token_encrypted))
    except Exception:
        pass  # best-effort — still unlink locally even if Plaid-side removal fails
    db.delete(item)
    db.commit()

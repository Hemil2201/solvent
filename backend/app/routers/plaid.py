import uuid
from datetime import date, datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db import get_db
from app.models.category import Category
from app.models.expense import Expense
from app.models.expense_split import ExpenseSplit
from app.models.plaid_account import PlaidAccount
from app.models.plaid_item import PlaidItem
from app.models.plaid_transaction import PlaidTransaction
from app.models.user import User
from app.schemas.plaid import (
    ExchangePublicTokenRequest,
    LinkTokenResponse,
    PlaidAccountOut,
    PlaidTransactionOut,
    ResolvePlaidTransactionRequest,
    SyncSummary,
)
from app.services import plaid_client
from app.services.categorizer import llm_category, rules_based_category
from app.services.duplicate_detector import find_duplicate
from app.services.split_calculator import SplitValidationError, compute_splits
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


def _user_items(db: Session, user_id: uuid.UUID) -> list[PlaidItem]:
    return list(db.execute(select(PlaidItem).where(PlaidItem.user_id == user_id, PlaidItem.status == "active")).scalars())


@router.post("/sync", response_model=SyncSummary)
def sync_transactions(db: Session = Depends(get_db), current_user: User = Depends(get_current_user)) -> SyncSummary:
    items = _user_items(db, current_user.id)
    categories = list(db.execute(select(Category)).scalars())
    categories_by_name = {c.name: c.id for c in categories}
    category_names = [c.name for c in categories]

    added_count = 0
    modified_count = 0
    removed_count = 0

    for item in items:
        accounts_by_plaid_id = {
            a.plaid_account_id: a
            for a in db.execute(select(PlaidAccount).where(PlaidAccount.plaid_item_id == item.id)).scalars()
        }
        result = plaid_client.sync_transactions(decrypt_token(item.access_token_encrypted), item.sync_cursor)

        for t in result["added"] + result["modified"]:
            account = accounts_by_plaid_id.get(t.account_id)
            if account is None:
                continue  # transaction on an account we haven't synced balances for yet — skip, next GET /accounts will catch it up

            existing = db.execute(
                select(PlaidTransaction).where(PlaidTransaction.plaid_transaction_id == t.transaction_id)
            ).scalar_one_or_none()

            description = t.merchant_name or t.name
            category_id = rules_based_category(description, categories_by_name)
            needs_clarification = False
            if category_id is None:
                llm_pick = llm_category(description, category_names)
                if llm_pick and llm_pick in categories_by_name:
                    category_id = categories_by_name[llm_pick]
                else:
                    needs_clarification = True

            duplicate_id = find_duplicate(db, t.date, t.amount) if existing is None else existing.is_duplicate_of

            if existing is None:
                db.add(
                    PlaidTransaction(
                        id=uuid.uuid4(),
                        plaid_account_id=account.id,
                        plaid_transaction_id=t.transaction_id,
                        raw_date=t.date,
                        raw_description=t.name,
                        raw_merchant_name=t.merchant_name,
                        raw_amount=t.amount,
                        pending=t.pending,
                        matched_category_id=category_id,
                        needs_clarification=needs_clarification,
                        is_duplicate_of=duplicate_id,
                        created_at=datetime.now(timezone.utc),
                    )
                )
                added_count += 1
            else:
                existing.raw_date = t.date
                existing.raw_description = t.name
                existing.raw_merchant_name = t.merchant_name
                existing.raw_amount = t.amount
                existing.pending = t.pending
                modified_count += 1

        for t in result["removed"]:
            removed = db.execute(
                select(PlaidTransaction).where(PlaidTransaction.plaid_transaction_id == t.transaction_id)
            ).scalar_one_or_none()
            if removed is not None and removed.resolved_expense_id is None:
                db.delete(removed)
                removed_count += 1

        item.sync_cursor = result["next_cursor"]
        item.updated_at = datetime.now(timezone.utc)
        db.commit()

    return SyncSummary(added=added_count, modified=modified_count, removed=removed_count)


def _txn_to_out(txn: PlaidTransaction, account_name: str, institution_name: str | None) -> PlaidTransactionOut:
    return PlaidTransactionOut(
        id=txn.id,
        account_name=account_name,
        institution_name=institution_name,
        raw_date=txn.raw_date,
        raw_description=txn.raw_description,
        raw_merchant_name=txn.raw_merchant_name,
        raw_amount=txn.raw_amount,
        pending=txn.pending,
        matched_category_id=txn.matched_category_id,
        needs_clarification=txn.needs_clarification,
        is_duplicate_of=txn.is_duplicate_of,
        resolved_expense_id=txn.resolved_expense_id,
        dismissed_at=txn.dismissed_at.isoformat() if txn.dismissed_at else None,
    )


@router.get("/transactions", response_model=list[PlaidTransactionOut])
def list_transactions(
    unresolved_only: bool = Query(True),
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> list[PlaidTransactionOut]:
    items = _user_items(db, current_user.id)
    results: list[PlaidTransactionOut] = []
    for item in items:
        accounts = list(db.execute(select(PlaidAccount).where(PlaidAccount.plaid_item_id == item.id)).scalars())
        for account in accounts:
            query = select(PlaidTransaction).where(PlaidTransaction.plaid_account_id == account.id)
            if unresolved_only:
                query = query.where(
                    PlaidTransaction.resolved_expense_id.is_(None), PlaidTransaction.dismissed_at.is_(None)
                )
            txns = db.execute(query.order_by(PlaidTransaction.raw_date.desc())).scalars()
            results.extend(_txn_to_out(t, account.name, item.institution_name) for t in txns)
    results.sort(key=lambda r: r.raw_date or date.min, reverse=True)
    return results


def _get_owned_transaction(db: Session, transaction_id: uuid.UUID, user_id: uuid.UUID) -> PlaidTransaction:
    txn = db.execute(
        select(PlaidTransaction)
        .join(PlaidAccount, PlaidTransaction.plaid_account_id == PlaidAccount.id)
        .join(PlaidItem, PlaidAccount.plaid_item_id == PlaidItem.id)
        .where(PlaidTransaction.id == transaction_id, PlaidItem.user_id == user_id)
    ).scalar_one_or_none()
    if txn is None:
        raise HTTPException(status_code=404, detail="Transaction not found")
    return txn


@router.post("/transactions/{transaction_id}/resolve", response_model=PlaidTransactionOut)
def resolve_transaction(
    transaction_id: uuid.UUID,
    body: ResolvePlaidTransactionRequest,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> PlaidTransactionOut:
    txn = _get_owned_transaction(db, transaction_id, current_user.id)
    if txn.resolved_expense_id is not None:
        raise HTTPException(status_code=409, detail="Transaction already resolved")

    if txn.is_duplicate_of is not None and body.confirm_duplicate:
        txn.resolved_expense_id = txn.is_duplicate_of
        db.commit()
        db.refresh(txn)
        account = db.execute(select(PlaidAccount).where(PlaidAccount.id == txn.plaid_account_id)).scalar_one()
        item = db.execute(select(PlaidItem).where(PlaidItem.id == account.plaid_item_id)).scalar_one()
        return _txn_to_out(txn, account.name, item.institution_name)

    if txn.raw_date is None or txn.raw_amount is None:
        raise HTTPException(status_code=422, detail="Transaction is missing a date or amount")

    account = db.execute(select(PlaidAccount).where(PlaidAccount.id == txn.plaid_account_id)).scalar_one()
    item = db.execute(select(PlaidItem).where(PlaidItem.id == account.plaid_item_id)).scalar_one()

    now = datetime.now(timezone.utc)
    # Plaid's sign convention (positive = money out) matches what this app
    # stores for a normal purchase — no sign flip needed here.
    expense = Expense(
        id=uuid.uuid4(),
        amount=txn.raw_amount,
        currency="USD",
        date=txn.raw_date,
        description=txn.raw_merchant_name or txn.raw_description,
        category_id=body.category_id or txn.matched_category_id,
        paid_by=body.paid_by or current_user.id,
        is_shared=body.is_shared,
        source="plaid_sync",
        source_transaction_id=txn.id,
        created_by=current_user.id,
        created_at=now,
        updated_at=now,
    )

    if body.is_shared:
        split_type = body.split_type or "equal"
        participants = list(db.execute(select(User.id).order_by(User.name)).scalars())
        raw_values = {s.user_id: s.value for s in body.splits} if body.splits else None
        try:
            amounts_owed = compute_splits(txn.raw_amount, split_type, participants, raw_values)
        except SplitValidationError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        expense.splits = [
            ExpenseSplit(id=uuid.uuid4(), user_id=user_id, split_type=split_type, amount_owed=amount)
            for user_id, amount in amounts_owed.items()
        ]

    db.add(expense)
    db.flush()
    txn.resolved_expense_id = expense.id
    db.commit()
    db.refresh(txn)
    return _txn_to_out(txn, account.name, item.institution_name)


@router.post("/transactions/{transaction_id}/dismiss", response_model=PlaidTransactionOut)
def dismiss_transaction(
    transaction_id: uuid.UUID, db: Session = Depends(get_db), current_user: User = Depends(get_current_user)
) -> PlaidTransactionOut:
    txn = _get_owned_transaction(db, transaction_id, current_user.id)
    if txn.resolved_expense_id is not None:
        raise HTTPException(status_code=409, detail="Transaction already resolved")
    txn.dismissed_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(txn)
    account = db.execute(select(PlaidAccount).where(PlaidAccount.id == txn.plaid_account_id)).scalar_one()
    item = db.execute(select(PlaidItem).where(PlaidItem.id == account.plaid_item_id)).scalar_one()
    return _txn_to_out(txn, account.name, item.institution_name)

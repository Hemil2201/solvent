from functools import lru_cache

import plaid
from plaid.api import plaid_api
from plaid.model.accounts_balance_get_request import AccountsBalanceGetRequest
from plaid.model.country_code import CountryCode
from plaid.model.institutions_get_by_id_request import InstitutionsGetByIdRequest
from plaid.model.institutions_get_by_id_request_options import InstitutionsGetByIdRequestOptions
from plaid.model.item_public_token_exchange_request import ItemPublicTokenExchangeRequest
from plaid.model.item_remove_request import ItemRemoveRequest
from plaid.model.link_token_create_request import LinkTokenCreateRequest
from plaid.model.link_token_create_request_user import LinkTokenCreateRequestUser
from plaid.model.products import Products
from plaid.model.sandbox_public_token_create_request import SandboxPublicTokenCreateRequest
from plaid.model.transactions_sync_request import TransactionsSyncRequest

from app.core.config import get_settings

settings = get_settings()

_ENVIRONMENTS = {"sandbox": plaid.Environment.Sandbox, "production": plaid.Environment.Production}


@lru_cache
def _client() -> plaid_api.PlaidApi:
    configuration = plaid.Configuration(
        host=_ENVIRONMENTS.get(settings.plaid_env, plaid.Environment.Sandbox),
        api_key={"clientId": settings.plaid_client_id, "secret": settings.plaid_secret},
    )
    return plaid_api.PlaidApi(plaid.ApiClient(configuration))


def create_link_token(user_id: str) -> str:
    """user_id here is our own User.id (not a Plaid id) — Plaid just uses it
    as an opaque client_user_id to scope the Link session."""
    request = LinkTokenCreateRequest(
        client_name="Solvent",
        language="en",
        country_codes=[CountryCode("US")],
        user=LinkTokenCreateRequestUser(client_user_id=user_id),
        products=[Products("transactions")],
    )
    response = _client().link_token_create(request)
    return response.link_token


def exchange_public_token(public_token: str) -> tuple[str, str]:
    """Returns (access_token, item_id). access_token must be encrypted
    (app/services/token_encryption.py) before it touches the DB."""
    response = _client().item_public_token_exchange(ItemPublicTokenExchangeRequest(public_token=public_token))
    return response.access_token, response.item_id


def get_institution_branding(institution_id: str) -> tuple[str | None, str | None]:
    """Returns (primary_color, logo) for an institution — color is a hex
    string (Plaid always returns one, real or a deterministic fallback),
    logo is a base64-encoded 152x152 PNG and genuinely None when Plaid
    doesn't have one on file. Best-effort: a lookup failure (unknown
    institution_id, API hiccup) shouldn't block linking the account, so the
    caller gets (None, None) instead of a raised exception."""
    try:
        response = _client().institutions_get_by_id(
            InstitutionsGetByIdRequest(
                institution_id=institution_id,
                country_codes=[CountryCode("US")],
                options=InstitutionsGetByIdRequestOptions(include_optional_metadata=True),
            )
        )
    except Exception:
        return None, None
    institution = response.institution
    return institution.primary_color, institution.logo


def get_balances(access_token: str) -> list[dict]:
    """One row per account on this Item: id, name, mask, type/subtype,
    current/available balance, currency — everything plaid_accounts needs."""
    response = _client().accounts_balance_get(AccountsBalanceGetRequest(access_token=access_token))
    return [
        {
            "account_id": a.account_id,
            "name": a.name,
            "official_name": a.official_name,
            "mask": a.mask,
            "type": str(a.type) if a.type else None,
            "subtype": str(a.subtype) if a.subtype else None,
            "current_balance": a.balances.current,
            "available_balance": a.balances.available,
            "iso_currency_code": a.balances.iso_currency_code,
        }
        for a in response.accounts
    ]


def sync_transactions(access_token: str, cursor: str | None) -> dict:
    """Wraps /transactions/sync, looping internally until has_more is False
    (Plaid's documented required pattern — a single page is not guaranteed
    to be the full delta). Returns {added, modified, removed, next_cursor}
    as raw Plaid model objects; the caller maps these into plaid_transactions
    rows."""
    added: list = []
    modified: list = []
    removed: list = []
    has_more = True
    while has_more:
        request = TransactionsSyncRequest(access_token=access_token, cursor=cursor) if cursor else TransactionsSyncRequest(
            access_token=access_token
        )
        response = _client().transactions_sync(request)
        added.extend(response.added)
        modified.extend(response.modified)
        removed.extend(response.removed)
        cursor = response.next_cursor
        has_more = response.has_more
    return {"added": added, "modified": modified, "removed": removed, "next_cursor": cursor}


def remove_item(access_token: str) -> None:
    """Revokes the Item on Plaid's side when a user unlinks an account —
    best-effort from the caller's perspective (the local PlaidItem row
    should be deleted regardless of whether this succeeds)."""
    _client().item_remove(ItemRemoveRequest(access_token=access_token))


def sandbox_create_public_token(institution_id: str = "ins_109508") -> str:
    """Sandbox only — bypasses the Link UI entirely and logs straight in
    with Plaid's default test credentials (user_good/pass_good) for the
    given institution, returning a public_token ready to exchange. Useful
    for a backend-only smoke test before any Android UI exists; ins_109508
    is Plaid's standard "First Platypus Bank" sandbox institution."""
    response = _client().sandbox_public_token_create(
        SandboxPublicTokenCreateRequest(institution_id=institution_id, initial_products=[Products("transactions")])
    )
    return response.public_token

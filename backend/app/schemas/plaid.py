import uuid
from datetime import date as date_type
from decimal import Decimal

from pydantic import BaseModel

from app.schemas.expense import SplitInput


class LinkTokenResponse(BaseModel):
    link_token: str


class ExchangePublicTokenRequest(BaseModel):
    public_token: str
    # Android passes these straight from LinkSuccess.metadata so we don't
    # need a separate /institutions lookup call just to label the account.
    institution_id: str | None = None
    institution_name: str | None = None


class PlaidAccountOut(BaseModel):
    id: uuid.UUID
    item_id: uuid.UUID
    name: str
    official_name: str | None
    mask: str | None
    type: str | None
    subtype: str | None
    current_balance: Decimal | None
    available_balance: Decimal | None
    iso_currency_code: str | None
    institution_name: str | None


class SyncSummary(BaseModel):
    added: int
    modified: int
    removed: int


class PlaidTransactionOut(BaseModel):
    id: uuid.UUID
    account_name: str
    institution_name: str | None
    raw_date: date_type | None
    raw_description: str | None
    raw_merchant_name: str | None
    raw_amount: Decimal | None
    pending: bool
    matched_category_id: uuid.UUID | None
    needs_clarification: bool
    is_duplicate_of: uuid.UUID | None
    resolved_expense_id: uuid.UUID | None
    dismissed_at: str | None = None
    # Suggestion derived from split_preferences — None on every field when
    # there's no merchant or category history yet, in which case the client
    # keeps its own "Personal" default.
    suggested_is_shared: bool | None = None
    suggested_split_type: str | None = None
    suggested_owed_by_other: bool = False
    suggested_split_values: dict[str, str] | None = None
    suggestion_sample_count: int | None = None


class ResolvePlaidTransactionRequest(BaseModel):
    category_id: uuid.UUID | None = None
    paid_by: uuid.UUID | None = None
    is_shared: bool = False
    split_type: str | None = None
    splits: list[SplitInput] | None = None
    confirm_duplicate: bool = False
    # Explicit flag rather than inferring from splits being 0/100 — those
    # could coincidentally match without being the dedicated "owed by the
    # other person" pattern, and the client already knows which it meant.
    owed_by_other: bool = False

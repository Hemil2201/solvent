import uuid
from decimal import Decimal

from pydantic import BaseModel


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

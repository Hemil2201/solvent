import uuid
from datetime import date as date_type
from datetime import datetime
from decimal import Decimal

from pydantic import BaseModel, ConfigDict


class SplitInput(BaseModel):
    user_id: uuid.UUID
    # Meaning depends on split_type: exact -> dollar amount, percentage -> 0-100, shares -> share count
    value: Decimal


class ExpenseCreate(BaseModel):
    amount: Decimal
    currency: str = "USD"
    date: date_type
    description: str | None = None
    notes: str | None = None
    category_id: uuid.UUID | None = None
    paid_by: uuid.UUID
    is_shared: bool = False
    split_type: str | None = None
    splits: list[SplitInput] | None = None
    receipt_photo_url: str | None = None


class ExpenseSplitOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    user_id: uuid.UUID
    split_type: str
    amount_owed: Decimal


class ExpenseOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    amount: Decimal
    currency: str
    date: date_type
    description: str | None
    notes: str | None
    category_id: uuid.UUID | None
    paid_by: uuid.UUID
    is_shared: bool
    receipt_photo_url: str | None
    merchant_logo_url: str | None
    refund_of_expense_id: uuid.UUID | None
    source: str
    created_by: uuid.UUID
    created_at: datetime
    updated_at: datetime
    deleted_at: datetime | None = None
    splits: list[ExpenseSplitOut] = []


class RefundSummaryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    amount: Decimal
    date: date_type


class ExpenseDetailOut(ExpenseOut):
    # Only populated by GET /expenses/{id} — kept off the base ExpenseOut
    # so list_expenses doesn't pay for a refunds lookup on every row.
    refunds: list[RefundSummaryOut] = []


class ExpenseUpdate(BaseModel):
    amount: Decimal | None = None
    currency: str | None = None
    date: date_type | None = None
    description: str | None = None
    notes: str | None = None
    category_id: uuid.UUID | None = None
    paid_by: uuid.UUID | None = None
    is_shared: bool | None = None
    split_type: str | None = None
    splits: list[SplitInput] | None = None


class ExpenseCommentCreate(BaseModel):
    comment: str


class ExpenseCommentOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    expense_id: uuid.UUID
    user_id: uuid.UUID
    comment: str
    created_at: datetime


class ExpenseEditHistoryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: uuid.UUID
    expense_id: uuid.UUID
    edited_by: uuid.UUID
    field_changed: str
    old_value: str | None
    new_value: str | None
    edited_at: datetime

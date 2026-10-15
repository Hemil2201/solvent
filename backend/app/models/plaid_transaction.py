import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Numeric, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class PlaidTransaction(Base):
    """Staging row, same role as StatementTransaction: a raw Plaid-synced
    transaction the user reviews and resolves into a real Expense. Private
    until resolved — only reachable through endpoints scoped to the owning
    PlaidItem's user_id. Amount sign follows Plaid's convention (positive =
    money out / a purchase, negative = money in / a refund or payment)."""

    __tablename__ = "plaid_transactions"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    plaid_account_id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), ForeignKey("plaid_accounts.id"), nullable=False
    )
    plaid_transaction_id: Mapped[str] = mapped_column(String, nullable=False, unique=True)
    raw_date: Mapped[date | None] = mapped_column(Date, nullable=True)
    raw_description: Mapped[str | None] = mapped_column(String, nullable=True)
    raw_merchant_name: Mapped[str | None] = mapped_column(String, nullable=True)
    raw_amount: Mapped[Decimal | None] = mapped_column(Numeric(12, 2), nullable=True)
    pending: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    matched_category_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("categories.id"), nullable=True
    )
    needs_clarification: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    user_clarification_note: Mapped[str | None] = mapped_column(String, nullable=True)
    is_duplicate_of: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("expenses.id"), nullable=True)
    resolved_expense_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("expenses.id"), nullable=True
    )
    # Set when the user explicitly skips a transaction that shouldn't become
    # an expense (a transfer between their own accounts, etc.).
    dismissed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

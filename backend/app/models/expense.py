import uuid
from datetime import date, datetime
from decimal import Decimal

from sqlalchemy import Boolean, Date, DateTime, ForeignKey, Numeric, String, exists
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, column_property, mapped_column, relationship

from app.db import Base


class Expense(Base):
    __tablename__ = "expenses"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    amount: Mapped[Decimal] = mapped_column(Numeric(12, 2), nullable=False)
    currency: Mapped[str] = mapped_column(String(3), nullable=False, default="USD")
    date: Mapped[date] = mapped_column(Date, nullable=False)
    description: Mapped[str | None] = mapped_column(String, nullable=True)
    notes: Mapped[str | None] = mapped_column(String, nullable=True)
    category_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("categories.id"))
    paid_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    is_shared: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    receipt_photo_url: Mapped[str | None] = mapped_column(String, nullable=True)
    # Carried over from the source PlaidTransaction's logo_url at resolve
    # time, if any — null for manual/statement/receipt-sourced expenses.
    merchant_logo_url: Mapped[str | None] = mapped_column(String, nullable=True)
    # Optional display/traceability link from a refund back to the
    # original purchase it's returning — purely informational, doesn't
    # affect any split/balance/budget math.
    refund_of_expense_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("expenses.id", ondelete="SET NULL"), nullable=True
    )
    source: Mapped[str] = mapped_column(String, nullable=False, default="manual")
    source_transaction_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), nullable=True)
    created_by: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), nullable=True)

    splits: Mapped[list["ExpenseSplit"]] = relationship(back_populates="expense", cascade="all, delete-orphan")


# Self-referential — defined after the class body since it needs Expense.id
# fully available. Computed in SQL (a correlated EXISTS) rather than a
# Python-side query per row, so it rides along with the normal list query
# at no extra round trip — lets the Expenses list show "this was refunded"
# without fetching each refund's full detail (that stays on the
# single-expense endpoint only, via ExpenseDetailOut.refunds).
#
# Built from the Core Table (Expense.__table__), not orm.aliased(Expense) —
# aliased() forces immediate mapper configuration at import time, which
# breaks if this module happens to be imported before app.models.expense_split
# registers the "ExpenseSplit" name that the splits relationship resolves
# lazily by string. Plain Table.alias() only touches column metadata, so it
# can't trip that ordering issue.
_refund_table = Expense.__table__.alias()
Expense.has_refunds = column_property(
    exists().where(_refund_table.c.refund_of_expense_id == Expense.__table__.c.id)
)

# Same merchant, within a day — informational only (never auto-merged),
# same spirit as duplicate_detector.find_duplicate but scanning already-
# resolved expenses against each other rather than an incoming Plaid
# transaction against history. Deliberately ignores amount: the real-world
# case this catches is a pending-vs-settled sync artifact where the two
# amounts differ slightly (e.g. a tip adjustment), which an exact-amount
# rule would miss entirely.
_possible_dup_table = Expense.__table__.alias()
Expense.has_possible_duplicate = column_property(
    exists().where(
        _possible_dup_table.c.id != Expense.__table__.c.id,
        _possible_dup_table.c.deleted_at.is_(None),
        _possible_dup_table.c.description == Expense.__table__.c.description,
        _possible_dup_table.c.date >= Expense.__table__.c.date - 1,
        _possible_dup_table.c.date <= Expense.__table__.c.date + 1,
    )
)

import uuid
from datetime import date as date_type
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy import select
from sqlalchemy.orm import Session, selectinload

from app.core.security import get_current_user
from app.db import get_db
from app.models.expense import Expense
from app.models.expense_comment import ExpenseComment
from app.models.expense_edit_history import ExpenseEditHistory
from app.models.expense_split import ExpenseSplit
from app.models.user import User
from app.schemas.expense import (
    DuplicateGroupOut,
    ExpenseCommentCreate,
    ExpenseCommentOut,
    ExpenseCreate,
    ExpenseDetailOut,
    ExpenseEditHistoryOut,
    ExpenseOut,
    ExpenseRefOut,
    ExpenseUpdate,
)
from app.services.split_calculator import SplitValidationError, compute_splits

# Fields tracked in expense_edit_history when changed via PUT.
_TRACKED_FIELDS = ["amount", "currency", "date", "description", "notes", "category_id", "paid_by", "is_shared"]

router = APIRouter(prefix="/expenses", tags=["expenses"])


@router.get("", response_model=list[ExpenseOut])
def list_expenses(
    start_date: date_type | None = None,
    end_date: date_type | None = None,
    category_id: uuid.UUID | None = None,
    person_id: uuid.UUID | None = None,
    is_shared: bool | None = None,
    deleted_only: bool = False,
    q: str | None = None,
    db: Session = Depends(get_db),
    _: User = Depends(get_current_user),
) -> list[Expense]:
    deleted_filter = Expense.deleted_at.isnot(None) if deleted_only else Expense.deleted_at.is_(None)
    query = select(Expense).options(selectinload(Expense.splits)).where(deleted_filter)
    if start_date is not None:
        query = query.where(Expense.date >= start_date)
    if end_date is not None:
        query = query.where(Expense.date <= end_date)
    if category_id is not None:
        query = query.where(Expense.category_id == category_id)
    if person_id is not None:
        query = query.where(Expense.paid_by == person_id)
    if is_shared is not None:
        query = query.where(Expense.is_shared == is_shared)
    if q:
        query = query.where(Expense.description.ilike(f"%{q}%"))
    query = query.order_by(Expense.date.desc(), Expense.created_at.desc())
    return list(db.execute(query).scalars())


@router.get("/duplicates", response_model=list[DuplicateGroupOut])
def find_duplicate_groups(db: Session = Depends(get_db), _: User = Depends(get_current_user)) -> list[DuplicateGroupOut]:
    """On-demand scan across all existing expenses — for cleaning up ones
    that already slipped through before Review Transactions could warn
    about them (or from before that warning existed at all). Groups
    same-merchant, same-day expenses together.

    Same-day rather than the Review-time check's +/-1 day window on
    purpose: tested against this account's real data, a window here swept
    in long runs of a frequently-visited merchant bought on genuinely
    separate consecutive days (three different Costco trips three days
    running, at three different amounts) as one false "duplicate" group.
    Every real duplicate found in that same data — Kroger, two insurance
    premiums, a water delivery, a takeout order — was same-day, several of
    them exact-amount matches too. The Review-time check still uses a
    wider window; comparing one new transaction against history doesn't
    have this chaining problem the same way a full-history scan does."""
    expenses = list(
        db.execute(
            select(Expense)
            .where(Expense.deleted_at.is_(None), Expense.description.isnot(None))
            .order_by(Expense.description, Expense.date)
        ).scalars()
    )

    groups: list[list[Expense]] = []
    current: list[Expense] = []
    for e in expenses:
        if current and current[-1].description == e.description and current[-1].date == e.date:
            current.append(e)
        else:
            if len(current) > 1:
                groups.append(current)
            current = [e]
    if len(current) > 1:
        groups.append(current)

    return [DuplicateGroupOut(expenses=[ExpenseRefOut.model_validate(e) for e in g]) for g in groups]


# IMPORTANT: this must stay below /duplicates (and any other static
# sub-path added under /expenses) — FastAPI matches routes in registration
# order, and {expense_id}'s uuid.UUID type would otherwise try (and fail)
# to parse "duplicates" as a UUID before ever reaching the route above.
@router.get("/{expense_id}", response_model=ExpenseDetailOut)
def get_expense(
    expense_id: uuid.UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)
) -> ExpenseDetailOut:
    expense = db.execute(
        select(Expense).options(selectinload(Expense.splits)).where(Expense.id == expense_id)
    ).scalar_one_or_none()
    if expense is None:
        raise HTTPException(status_code=404, detail="Expense not found")

    refunds = db.execute(
        select(Expense).where(Expense.refund_of_expense_id == expense_id, Expense.deleted_at.is_(None))
    ).scalars()

    refund_of = None
    if expense.refund_of_expense_id is not None:
        parent = db.execute(select(Expense).where(Expense.id == expense.refund_of_expense_id)).scalar_one_or_none()
        if parent is not None:
            refund_of = ExpenseRefOut.model_validate(parent)

    return ExpenseDetailOut.model_validate(expense).model_copy(
        update={
            "refunds": [ExpenseRefOut.model_validate(r) for r in refunds],
            "refund_of": refund_of,
        }
    )


@router.post("", response_model=ExpenseOut)
def create_expense(
    body: ExpenseCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Expense:
    now = datetime.now(timezone.utc)
    expense = Expense(
        id=uuid.uuid4(),
        amount=body.amount,
        currency=body.currency,
        date=body.date,
        description=body.description,
        notes=body.notes,
        category_id=body.category_id,
        paid_by=body.paid_by,
        is_shared=body.is_shared,
        receipt_photo_url=body.receipt_photo_url,
        source="receipt_scan" if body.receipt_photo_url else "manual",
        created_by=current_user.id,
        created_at=now,
        updated_at=now,
    )

    if body.is_shared:
        split_type = body.split_type or "equal"
        participants = list(db.execute(select(User.id).order_by(User.name)).scalars())
        raw_values = {s.user_id: s.value for s in body.splits} if body.splits else None
        try:
            amounts_owed = compute_splits(body.amount, split_type, participants, raw_values)
        except SplitValidationError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

        expense.splits = [
            ExpenseSplit(id=uuid.uuid4(), user_id=user_id, split_type=split_type, amount_owed=amount)
            for user_id, amount in amounts_owed.items()
        ]

    db.add(expense)
    db.commit()
    db.refresh(expense)
    return expense


@router.put("/{expense_id}", response_model=ExpenseOut)
def update_expense(
    expense_id: uuid.UUID,
    body: ExpenseUpdate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> Expense:
    expense = db.execute(
        select(Expense).options(selectinload(Expense.splits)).where(Expense.id == expense_id)
    ).scalar_one_or_none()
    if expense is None:
        raise HTTPException(status_code=404, detail="Expense not found")
    if expense.deleted_at is not None:
        raise HTTPException(status_code=409, detail="Cannot edit a deleted expense — restore it first")

    now = datetime.now(timezone.utc)
    updates = body.model_dump(exclude_unset=True, exclude={"split_type", "splits"})

    for field in _TRACKED_FIELDS:
        if field not in updates:
            continue
        old_value = getattr(expense, field)
        new_value = updates[field]
        if old_value == new_value:
            continue
        db.add(
            ExpenseEditHistory(
                id=uuid.uuid4(),
                expense_id=expense.id,
                edited_by=current_user.id,
                field_changed=field,
                old_value=str(old_value) if old_value is not None else None,
                new_value=str(new_value) if new_value is not None else None,
                edited_at=now,
            )
        )
        setattr(expense, field, new_value)

    # is_shared changed, or new split config given for an already-shared expense -> recompute splits.
    recompute_splits = "is_shared" in updates or body.split_type is not None or body.splits is not None
    if recompute_splits:
        if expense.is_shared:
            split_type = body.split_type or (expense.splits[0].split_type if expense.splits else "equal")
            participants = list(db.execute(select(User.id).order_by(User.name)).scalars())
            raw_values = {s.user_id: s.value for s in body.splits} if body.splits else None
            try:
                amounts_owed = compute_splits(expense.amount, split_type, participants, raw_values)
            except SplitValidationError as exc:
                raise HTTPException(status_code=422, detail=str(exc)) from exc
            expense.splits = [
                ExpenseSplit(id=uuid.uuid4(), user_id=user_id, split_type=split_type, amount_owed=amount)
                for user_id, amount in amounts_owed.items()
            ]
        else:
            expense.splits = []

    expense.updated_at = now
    db.commit()
    db.refresh(expense)
    return expense


@router.delete("/{expense_id}", response_model=ExpenseOut)
def delete_expense(
    expense_id: uuid.UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)
) -> Expense:
    expense = db.execute(select(Expense).where(Expense.id == expense_id)).scalar_one_or_none()
    if expense is None:
        raise HTTPException(status_code=404, detail="Expense not found")
    expense.deleted_at = datetime.now(timezone.utc)
    db.commit()
    db.refresh(expense)
    return expense


@router.post("/{expense_id}/restore", response_model=ExpenseOut)
def restore_expense(
    expense_id: uuid.UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)
) -> Expense:
    expense = db.execute(select(Expense).where(Expense.id == expense_id)).scalar_one_or_none()
    if expense is None:
        raise HTTPException(status_code=404, detail="Expense not found")
    expense.deleted_at = None
    db.commit()
    db.refresh(expense)
    return expense


@router.get("/{expense_id}/comments", response_model=list[ExpenseCommentOut])
def list_comments(
    expense_id: uuid.UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)
) -> list[ExpenseComment]:
    return list(
        db.execute(
            select(ExpenseComment)
            .where(ExpenseComment.expense_id == expense_id)
            .order_by(ExpenseComment.created_at)
        ).scalars()
    )


@router.post("/{expense_id}/comments", response_model=ExpenseCommentOut)
def add_comment(
    expense_id: uuid.UUID,
    body: ExpenseCommentCreate,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> ExpenseComment:
    comment = ExpenseComment(
        id=uuid.uuid4(),
        expense_id=expense_id,
        user_id=current_user.id,
        comment=body.comment,
        created_at=datetime.now(timezone.utc),
    )
    db.add(comment)
    db.commit()
    db.refresh(comment)
    return comment


@router.get("/{expense_id}/history", response_model=list[ExpenseEditHistoryOut])
def get_history(
    expense_id: uuid.UUID, db: Session = Depends(get_db), _: User = Depends(get_current_user)
) -> list[ExpenseEditHistory]:
    return list(
        db.execute(
            select(ExpenseEditHistory)
            .where(ExpenseEditHistory.expense_id == expense_id)
            .order_by(ExpenseEditHistory.edited_at.desc())
        ).scalars()
    )

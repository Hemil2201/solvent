import uuid
from datetime import datetime, timezone
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.orm import Session

from app.models.split_preference import SplitPreference


def normalize_merchant_key(raw: str | None) -> str | None:
    """Same normalization the categorizer applies to descriptions, so
    "Uber Trip Help.Uber.Com" and "UBER TRIP 8832" land on the same key."""
    if raw is None:
        return None
    cleaned = raw.strip().upper()
    return cleaned or None


def get_suggestion(db: Session, merchant_key: str | None, category_id: uuid.UUID | None) -> SplitPreference | None:
    """Merchant-specific history first (most specific signal); falls back to
    category-wide history when this exact merchant has never been resolved
    before. Returns None when neither exists yet — caller keeps today's
    "Personal" default in that case."""
    if merchant_key is not None:
        match = db.execute(
            select(SplitPreference).where(SplitPreference.merchant_key == merchant_key)
        ).scalar_one_or_none()
        if match is not None:
            return match

    if category_id is not None:
        return db.execute(
            select(SplitPreference).where(
                SplitPreference.category_id == category_id, SplitPreference.merchant_key.is_(None)
            )
        ).scalar_one_or_none()

    return None


def record_choice(
    db: Session,
    merchant_key: str | None,
    category_id: uuid.UUID | None,
    is_shared: bool,
    split_type: str | None,
    owed_by_other: bool,
    split_values: dict[str, Decimal] | None,
) -> None:
    """Updates both the merchant-specific and category-wide preference rows
    (whichever keys are available) with whatever was actually just chosen —
    this runs on every resolve, so an override of a prior suggestion is
    exactly how the preference keeps adapting, no special-casing needed."""
    now = datetime.now(timezone.utc)
    values_as_str = {str(k): str(v) for k, v in split_values.items()} if split_values else None

    if merchant_key is not None:
        _upsert(db, SplitPreference.merchant_key == merchant_key, merchant_key, None, is_shared, split_type, owed_by_other, values_as_str, now)

    if category_id is not None:
        _upsert(
            db,
            (SplitPreference.category_id == category_id) & (SplitPreference.merchant_key.is_(None)),
            None,
            category_id,
            is_shared,
            split_type,
            owed_by_other,
            values_as_str,
            now,
        )


def _upsert(db, where_clause, merchant_key, category_id, is_shared, split_type, owed_by_other, split_values, now) -> None:
    existing = db.execute(select(SplitPreference).where(where_clause)).scalar_one_or_none()
    if existing is not None:
        existing.is_shared = is_shared
        existing.split_type = split_type
        existing.owed_by_other = owed_by_other
        existing.split_values = split_values
        existing.sample_count += 1
        existing.updated_at = now
    else:
        db.add(
            SplitPreference(
                id=uuid.uuid4(),
                merchant_key=merchant_key,
                category_id=category_id,
                is_shared=is_shared,
                split_type=split_type,
                owed_by_other=owed_by_other,
                split_values=split_values,
                sample_count=1,
                updated_at=now,
            )
        )

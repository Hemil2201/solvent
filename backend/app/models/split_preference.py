import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Integer, String
from sqlalchemy.dialects.postgresql import JSONB, UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class SplitPreference(Base):
    """Learned split suggestion. Starts empty — only written to as the user
    resolves shared transactions going forward, no backfill from existing
    expense history. Keyed by normalized merchant name first (most specific
    signal), falling back to category when no merchant-specific history
    exists yet — exactly one of merchant_key/category_id is set per row."""

    __tablename__ = "split_preferences"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    merchant_key: Mapped[str | None] = mapped_column(String, nullable=True)
    category_id: Mapped[uuid.UUID | None] = mapped_column(UUID(as_uuid=True), ForeignKey("categories.id"), nullable=True)
    split_type: Mapped[str] = mapped_column(String, nullable=False)
    # Per-user split values as last chosen, e.g. {"<user-uuid>": "50", ...}
    # for percentage/shares; null for equal splits.
    split_values: Mapped[dict | None] = mapped_column(JSONB, nullable=True)
    sample_count: Mapped[int] = mapped_column(Integer, nullable=False, default=1)
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

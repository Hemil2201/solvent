import uuid
from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, String
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import Mapped, mapped_column

from app.db import Base


class PlaidItem(Base):
    __tablename__ = "plaid_items"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    # Private to whoever linked it — every /plaid endpoint filters by this
    # against current_user.id, not just a UI-level hide.
    user_id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), ForeignKey("users.id"), nullable=False)
    plaid_item_id: Mapped[str] = mapped_column(String, nullable=False, unique=True)
    plaid_institution_id: Mapped[str | None] = mapped_column(String, nullable=True)
    institution_name: Mapped[str | None] = mapped_column(String, nullable=True)
    # Hex string from Plaid's /institutions/get_by_id — real brand color per
    # bank, used for the account card accent. Plaid returns a deterministic
    # fallback when it has no real color on file, so this is effectively
    # never null for an institution we could look up.
    institution_color: Mapped[str | None] = mapped_column(String, nullable=True)
    # Base64-encoded 152x152 PNG, same /institutions/get_by_id response —
    # genuinely null for institutions Plaid has no logo for (unlike color,
    # which always has a fallback). Client falls back to the color+initial
    # badge in that case.
    institution_logo: Mapped[str | None] = mapped_column(String, nullable=True)
    # Fernet-encrypted (app/services/token_encryption.py) — decrypt only
    # in-memory, right before a Plaid API call. Never log or return this.
    access_token_encrypted: Mapped[str] = mapped_column(String, nullable=False)
    sync_cursor: Mapped[str | None] = mapped_column(String, nullable=True)
    status: Mapped[str] = mapped_column(String, nullable=False, default="active")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    updated_at: Mapped[datetime] = mapped_column(DateTime(timezone=True))

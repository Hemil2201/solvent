import io
import logging
import uuid

from fastapi import APIRouter, Depends, HTTPException, UploadFile
from PIL import Image, ImageOps
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.core.security import get_current_user
from app.db import get_db
from app.models.category import Category
from app.models.user import User
from app.schemas.receipt import ReceiptScanResponse
from app.services.categorizer import rules_based_category
from app.services.receipt_scanner import scan_receipt
from app.services.storage_service import upload_receipt

logger = logging.getLogger(__name__)
router = APIRouter(prefix="/receipts", tags=["receipts"])

ALLOWED_CONTENT_TYPES = {"image/jpeg", "image/png", "image/webp", "image/heic"}

MAX_DIMENSION = 1600  # px — plenty to keep a receipt legible, far below a raw camera photo


def _compress(raw_bytes: bytes, content_type: str) -> tuple[bytes, str]:
    """Downscales and re-encodes as JPEG so what we store/send is a fraction
    of a raw camera photo's size. Falls back to the original bytes on any
    decode failure (e.g. HEIC, which Pillow can't open without a plugin)
    rather than blocking the upload."""
    try:
        image = Image.open(io.BytesIO(raw_bytes))
        # Phone photos carry an EXIF orientation tag ("display this rotated
        # 90°") rather than storing the pixels upright — PIL doesn't apply it
        # automatically, and re-encoding without it bakes in the sideways
        # pixels permanently. That's what was feeding the AI a rotated
        # receipt and causing it to misread the printed date.
        image = ImageOps.exif_transpose(image)
        image = image.convert("RGB")
        image.thumbnail((MAX_DIMENSION, MAX_DIMENSION))
        buf = io.BytesIO()
        image.save(buf, format="JPEG", quality=82)
        return buf.getvalue(), "image/jpeg"
    except Exception as exc:
        logger.warning("Receipt image compression skipped: %s", exc)
        return raw_bytes, content_type


@router.post("/scan", response_model=ReceiptScanResponse)
async def scan(
    file: UploadFile,
    db: Session = Depends(get_db),
    current_user: User = Depends(get_current_user),
) -> ReceiptScanResponse:
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=422, detail="Receipt must be a JPEG, PNG, WebP, or HEIC image")

    raw_bytes = await file.read()
    content, content_type = _compress(raw_bytes, file.content_type)

    extension = content_type.split("/")[1]
    path = f"{current_user.id}/{uuid.uuid4()}.{extension}"
    photo_url = upload_receipt(path, content, content_type)

    result = scan_receipt(content, content_type)

    category_id = None
    if result.merchant:
        categories_by_name = {c.name: c.id for c in db.execute(select(Category)).scalars()}
        category_id = rules_based_category(result.merchant, categories_by_name)

    return ReceiptScanResponse(
        date=result.date,
        description=result.merchant,
        amount=result.amount,
        category_id=category_id,
        receipt_photo_url=photo_url,
    )

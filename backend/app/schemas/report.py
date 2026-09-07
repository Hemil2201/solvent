import uuid
from datetime import date as date_type
from decimal import Decimal

from pydantic import BaseModel


class CategoryBreakdown(BaseModel):
    category_id: uuid.UUID | None
    category_name: str
    total: Decimal


class MonthlyReport(BaseModel):
    # Populated for a whole-month query; null for a custom start/end range.
    month: int | None = None
    year: int | None = None
    # Populated for a custom start/end range; null for a whole-month query.
    start_date: date_type | None = None
    end_date: date_type | None = None
    total_spend: Decimal
    personal_spend: Decimal
    shared_spend: Decimal
    by_category: list[CategoryBreakdown]

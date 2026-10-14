from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.routers import (
    activity,
    auth,
    balance,
    budgets,
    categories,
    expenses,
    plaid,
    receipts,
    recurring,
    reports,
    statements,
    users,
)

app = FastAPI(title="Expense Splitter API")

# 2-user local app: dev CORS is wide open (Android emulator, physical device
# on the same LAN, etc.) — tighten only if this ever moves beyond Stage 5.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(auth.router)
app.include_router(categories.router)
app.include_router(expenses.router)
app.include_router(balance.router)
app.include_router(budgets.router)
app.include_router(statements.router)
app.include_router(reports.router)
app.include_router(recurring.router)
app.include_router(activity.router)
app.include_router(users.router)
app.include_router(receipts.router)
app.include_router(plaid.router)


@app.get("/health")
def health():
    return {"status": "ok"}

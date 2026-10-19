-- Expense Splitter — Phase 1 schema
-- Source of truth for entities/relationships: project-plan/03_SCHEMA.md
-- Loans/IOU tables intentionally excluded (Phase 2).

create extension if not exists pgcrypto;

create or replace function set_updated_at()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

-- ============================================================== users ====
create table users (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  email text not null unique,
  avatar_url text,
  created_at timestamptz not null default now(),
  -- bcrypt hash — never store the raw PIN. Login is otherwise just
  -- "pick your name," so this is the only thing standing between the
  -- public API and full account access.
  pin_hash text,
  failed_login_attempts integer not null default 0,
  locked_until timestamptz
);

-- ========================================================= categories ====
create table categories (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  icon text,
  is_default boolean not null default false
);

-- ============================================================ budgets ====
create table budgets (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references users(id) on delete cascade,
  category_id uuid not null references categories(id) on delete cascade,
  month int not null check (month between 1 and 12),
  year int not null check (year between 2000 and 2100),
  target_amount numeric(12, 2) not null check (target_amount >= 0)
);

-- user_id is nullable: null = group-level target. A plain UNIQUE constraint
-- treats NULLs as distinct, so it wouldn't stop two group-level rows for the
-- same category/month/year — use partial unique indexes instead.
create unique index budgets_personal_unique
  on budgets (user_id, category_id, month, year)
  where user_id is not null;

create unique index budgets_group_unique
  on budgets (category_id, month, year)
  where user_id is null;

-- =========================================================== expenses ====
create table expenses (
  id uuid primary key default gen_random_uuid(),
  -- No lower bound: a refund/return is its own Expense with a negative
  -- amount (same sign convention Plaid uses), not a reduction applied to
  -- the original purchase — nets out correctly in balance/budget totals
  -- with no special-casing needed there.
  amount numeric(12, 2) not null,
  currency varchar(3) not null default 'USD',
  date date not null,
  description text,
  notes text,
  category_id uuid references categories(id),
  paid_by uuid not null references users(id),
  is_shared boolean not null default false,
  receipt_photo_url text,
  -- Carried over from the source PlaidTransaction's logo_url at resolve
  -- time, if any — null for manual/statement/receipt-sourced expenses.
  merchant_logo_url text,
  -- Optional display/traceability link from a refund back to the
  -- original purchase it's returning — purely informational, doesn't
  -- affect any split/balance/budget math.
  refund_of_expense_id uuid references expenses(id) on delete set null,
  source text not null default 'manual'
    check (source in ('manual', 'statement_upload', 'receipt_scan', 'recurring')),
  -- FK to statement_transactions added below (after that table exists) to
  -- break the circular reference between expenses <-> statement_transactions.
  source_transaction_id uuid,
  created_by uuid not null references users(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

create trigger expenses_set_updated_at
  before update on expenses
  for each row execute function set_updated_at();

create index expenses_date_idx on expenses (date);
create index expenses_category_idx on expenses (category_id);
create index expenses_paid_by_idx on expenses (paid_by);
create index expenses_is_shared_idx on expenses (is_shared);
create index expenses_deleted_at_idx on expenses (deleted_at);
create index expenses_refund_of_idx on expenses (refund_of_expense_id);

-- ===================================================== expense_splits ====
-- Only populated when expenses.is_shared = true. Drives both the running
-- balance calculation and which users' personal budgets get debited.
create table expense_splits (
  id uuid primary key default gen_random_uuid(),
  expense_id uuid not null references expenses(id) on delete cascade,
  user_id uuid not null references users(id),
  split_type text not null
    check (split_type in ('equal', 'percentage', 'exact', 'shares')),
  -- No lower bound, matching expenses.amount — a shared refund's splits
  -- are negative too (money owed back, not owed).
  amount_owed numeric(12, 2) not null,
  unique (expense_id, user_id)
);

create index expense_splits_expense_idx on expense_splits (expense_id);
create index expense_splits_user_idx on expense_splits (user_id);

-- =================================================== expense_comments ====
create table expense_comments (
  id uuid primary key default gen_random_uuid(),
  expense_id uuid not null references expenses(id) on delete cascade,
  user_id uuid not null references users(id),
  comment text not null,
  created_at timestamptz not null default now()
);

create index expense_comments_expense_idx on expense_comments (expense_id);

-- ============================================== expense_edit_history ====
create table expense_edit_history (
  id uuid primary key default gen_random_uuid(),
  expense_id uuid not null references expenses(id) on delete cascade,
  edited_by uuid not null references users(id),
  field_changed text not null,
  old_value text,
  new_value text,
  edited_at timestamptz not null default now()
);

create index expense_edit_history_expense_idx on expense_edit_history (expense_id);

-- =================================================== recurring_expenses ====
create table recurring_expenses (
  id uuid primary key default gen_random_uuid(),
  amount numeric(12, 2) not null check (amount >= 0),
  currency varchar(3) not null default 'USD',
  category_id uuid references categories(id),
  description text,
  paid_by uuid not null references users(id),
  is_shared boolean not null default false,
  default_split_config jsonb,
  frequency text not null
    check (frequency in ('weekly', 'fortnightly', 'monthly', 'yearly')),
  next_run_date date not null,
  is_active boolean not null default true,
  created_by uuid not null references users(id),
  created_at timestamptz not null default now()
);

create index recurring_expenses_next_run_idx on recurring_expenses (next_run_date)
  where is_active = true;

-- ===================================================== statement_uploads ====
create table statement_uploads (
  id uuid primary key default gen_random_uuid(),
  uploaded_by uuid not null references users(id),
  file_url text not null,
  bank_name text,
  card_last4 varchar(4),
  upload_date timestamptz not null default now(),
  status text not null default 'processing'
    check (status in ('processing', 'needs_review', 'completed', 'failed')),
  -- Set right after parsing determines the transaction count, before the
  -- per-row categorization loop starts — lets clients show "X of Y" progress
  -- while status is still 'processing' instead of a bare spinner.
  expected_transaction_count integer
);

-- ================================================= statement_transactions ====
create table statement_transactions (
  id uuid primary key default gen_random_uuid(),
  statement_upload_id uuid not null references statement_uploads(id) on delete cascade,
  raw_date date,
  raw_description text,
  raw_amount numeric(12, 2),
  matched_category_id uuid references categories(id),
  needs_clarification boolean not null default false,
  user_clarification_note text,
  is_duplicate_of uuid references expenses(id),
  resolved_expense_id uuid references expenses(id),
  created_at timestamptz not null default now()
);

create index statement_transactions_upload_idx
  on statement_transactions (statement_upload_id);

-- ======================================================== plaid_items ====
-- One Plaid "Item" = one end-user's login connection to one institution
-- (can contain several accounts). Private to whoever linked it — user_id is
-- an access-control boundary, not just metadata: every /plaid endpoint
-- filters by current_user.id, so Hemil's items are never queryable from
-- Heenal's session and vice versa.
create table plaid_items (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references users(id) on delete cascade,
  plaid_item_id text not null unique,
  plaid_institution_id text,
  institution_name text,
  -- Both from Plaid's /institutions/get_by_id, fetched once at link time.
  -- Color is a hex string (Plaid always returns one — real or a
  -- deterministic fallback); logo is a base64-encoded 152x152 PNG and
  -- genuinely null for institutions Plaid has no logo for.
  institution_color text,
  institution_logo text,
  -- Fernet-encrypted at rest (app/services/token_encryption.py) — this is
  -- the one real secret in the whole DB, never logged or returned to the
  -- client in any response.
  access_token_encrypted text not null,
  -- Cursor for the /transactions/sync pagination endpoint; null until the
  -- first sync.
  sync_cursor text,
  status text not null default 'active'
    check (status in ('active', 'error', 'revoked')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create trigger plaid_items_set_updated_at
  before update on plaid_items
  for each row execute function set_updated_at();

create index plaid_items_user_idx on plaid_items (user_id);

-- ===================================================== plaid_accounts ====
create table plaid_accounts (
  id uuid primary key default gen_random_uuid(),
  plaid_item_id uuid not null references plaid_items(id) on delete cascade,
  plaid_account_id text not null unique,
  name text not null,
  official_name text,
  mask varchar(4),
  type text,
  subtype text,
  current_balance numeric(12, 2),
  available_balance numeric(12, 2),
  iso_currency_code varchar(3),
  updated_at timestamptz not null default now()
);

create trigger plaid_accounts_set_updated_at
  before update on plaid_accounts
  for each row execute function set_updated_at();

create index plaid_accounts_item_idx on plaid_accounts (plaid_item_id);

-- ================================================= plaid_transactions ====
-- Staging table, same shape/role as statement_transactions (raw row ->
-- user reviews -> resolve into a real expense) but sourced from a Plaid
-- account's sync feed instead of an uploaded file. Private until resolved:
-- only visible through endpoints scoped to the owning plaid_item's user_id.
-- Once resolved_expense_id is set, the resulting Expense is a normal shared
-- row like any other — privacy only applies to this staging data.
--
-- Amount sign follows Plaid's convention: positive = money out (a purchase,
-- what we want), negative = money in (a refund/credit/payment) — so these
-- can be stored and summed the same way a signed refund expense would be,
-- without an abs()/sign-flip step.
create table plaid_transactions (
  id uuid primary key default gen_random_uuid(),
  plaid_account_id uuid not null references plaid_accounts(id) on delete cascade,
  plaid_transaction_id text not null unique,
  raw_date date,
  raw_description text,
  raw_merchant_name text,
  raw_amount numeric(12, 2),
  pending boolean not null default false,
  -- Plaid-hosted merchant logo URL for this specific transaction, when
  -- Plaid has one — commonly null for smaller/less-enriched merchants.
  logo_url text,
  matched_category_id uuid references categories(id),
  needs_clarification boolean not null default false,
  user_clarification_note text,
  is_duplicate_of uuid references expenses(id),
  resolved_expense_id uuid references expenses(id),
  -- Set when the user explicitly skips a transaction that shouldn't become
  -- an expense (a transfer between their own accounts, etc.) — distinct
  -- from resolved_expense_id so "dismissed" and "turned into an expense"
  -- are never confused in the review queue's unresolved-count.
  dismissed_at timestamptz,
  created_at timestamptz not null default now()
);

create index plaid_transactions_account_idx on plaid_transactions (plaid_account_id);
create index plaid_transactions_resolved_idx on plaid_transactions (resolved_expense_id);

-- =================================================== split_preferences ====
-- Learned split suggestions (starts empty — learns only from choices made
-- going forward, no backfill from existing expense history). Keyed by
-- normalized merchant name first (most specific signal), falling back to
-- category when no merchant-specific history exists yet. Exactly one of
-- merchant_key/category_id is set per row.
create table split_preferences (
  id uuid primary key default gen_random_uuid(),
  merchant_key text,
  category_id uuid references categories(id) on delete cascade,
  is_shared boolean not null default false,
  -- Only meaningful when is_shared — which split algorithm, mirroring
  -- expenses.splits.split_type. Null for a personal-only history.
  split_type text
    check (split_type is null or split_type in ('equal', 'percentage', 'exact', 'shares')),
  -- Marks the one-tap "owed by the other person" pattern specifically
  -- (full amount to whoever didn't pay, 0 to the payer) so a future
  -- suggestion re-selects that chip directly rather than a generic exact
  -- split with raw numbers that happen to be 0/100.
  owed_by_other boolean not null default false,
  -- Per-user split values as last chosen (e.g. {"<user-uuid>": "50", ...})
  -- for a non-equal general Shared split; null for equal/owed_by_other.
  -- Reapplied as a starting suggestion against the new amount, not
  -- force-matched.
  split_values jsonb,
  sample_count integer not null default 1,
  updated_at timestamptz not null default now()
);

create unique index split_preferences_merchant_unique
  on split_preferences (merchant_key)
  where merchant_key is not null;

create unique index split_preferences_category_unique
  on split_preferences (category_id)
  where merchant_key is null and category_id is not null;

create trigger split_preferences_set_updated_at
  before update on split_preferences
  for each row execute function set_updated_at();

-- Close the circular reference now that statement_transactions exists.
-- source_transaction_id is intentionally a loose pointer rather than a
-- single-target FK: it can reference either statement_transactions.id or
-- plaid_transactions.id depending on source, so no FK is declared on it at
-- all (matches the ORM model, which never declared a relationship here).
alter table expenses
  drop constraint if exists expenses_source_transaction_fk;

alter table expenses
  drop constraint if exists expenses_source_check;

alter table expenses
  add constraint expenses_source_check
  check (source in ('manual', 'statement_upload', 'receipt_scan', 'recurring', 'plaid_sync'));

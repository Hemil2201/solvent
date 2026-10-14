from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    database_url: str
    supabase_url: str = ""
    supabase_anon_key: str = ""
    supabase_service_role_key: str = ""

    jwt_secret: str
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 60 * 24 * 30

    anthropic_api_key: str = ""

    # Sandbox for now — see backend/.env.example for where to get these.
    plaid_client_id: str = ""
    plaid_secret: str = ""
    plaid_env: str = "sandbox"
    # Fernet key (Fernet.generate_key()) encrypting plaid_items.access_token
    # at rest — the one real secret this app stores in its own DB. Separate
    # from jwt_secret: a leaked JWT signing key shouldn't also unlock stored
    # bank access tokens.
    plaid_encryption_key: str = ""

    env: str = "dev"


@lru_cache
def get_settings() -> Settings:
    return Settings()

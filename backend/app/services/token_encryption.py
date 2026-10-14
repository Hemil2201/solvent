from functools import lru_cache

from cryptography.fernet import Fernet, InvalidToken

from app.core.config import get_settings

settings = get_settings()


class TokenEncryptionError(Exception):
    pass


@lru_cache
def _fernet() -> Fernet:
    if not settings.plaid_encryption_key:
        raise TokenEncryptionError(
            "PLAID_ENCRYPTION_KEY is not set — generate one with "
            "`python -c \"from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())\"`"
        )
    try:
        return Fernet(settings.plaid_encryption_key.encode())
    except (ValueError, TypeError) as exc:
        raise TokenEncryptionError("PLAID_ENCRYPTION_KEY is not a valid Fernet key") from exc


def encrypt_token(raw_token: str) -> str:
    """Encrypts a Plaid access token for storage. Returns a str safe to put
    straight into the access_token_encrypted column."""
    return _fernet().encrypt(raw_token.encode()).decode()


def decrypt_token(encrypted_token: str) -> str:
    """Reverses encrypt_token. Only ever call this in-memory, right before a
    Plaid API call — the decrypted value must never be logged, returned in
    an API response, or persisted anywhere else."""
    try:
        return _fernet().decrypt(encrypted_token.encode()).decode()
    except InvalidToken as exc:
        raise TokenEncryptionError("Stored token could not be decrypted — wrong key, or data is corrupt") from exc

from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "niro-ace"
    app_env: str = "development"
    database_url: str = "postgresql+psycopg://postgres:postgres@localhost:5432/niro_ace"

    model_config = SettingsConfigDict(env_prefix="NIRO_ACE_", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()

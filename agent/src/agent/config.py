from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    """Environment-backed configuration.

    ``internal_token`` has no default on purpose: it is the shared secret the Java API and this
    service authenticate each other with, so an unset value must stop the service booting rather
    than silently open it up.
    """

    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    internal_token: str

    qdrant_url: str = ""
    qdrant_api_key: str = ""

    anthropic_api_key: str = ""
    # Embeddings only — see README.
    openai_api_key: str = ""

    pulse_api_url: str = "http://localhost:8080"

    # Wired in issue 10.
    langfuse_public_key: str = ""
    langfuse_secret_key: str = ""
    langfuse_host: str = "https://us.cloud.langfuse.com"


settings = Settings()

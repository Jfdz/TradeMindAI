from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    database_url: str
    rabbitmq_url: str = "amqp://guest:guest@localhost"
    model_path: str = "./models/"
    enable_gpu: bool = False
    cors_allowed_origins: str = "http://localhost:3000,http://127.0.0.1:3000"
    market_data_service_url: str = "http://market-data-service:8081"
    internal_secret: str = ""
    market_data_internal_secret: str = ""
    trading_core_service_url: str = "https://trading-core-service:8082"
    trading_core_internal_secret: str = ""
    # C4 — LLM reasoning provider selection.
    #   "stub" (default): no LLM, every call returns REFUSED_LLM_DISABLED.
    #   "anthropic_oauth": uses CLAUDE_CODE_OAUTH_TOKEN (Claude Max). Temporary.
    #   "anthropic_api_key": uses ANTHROPIC_API_KEY (paid API tier). Target end state.
    llm_provider: str = "stub"
    claude_code_oauth_token: str = ""
    anthropic_api_key: str = ""
    anthropic_model: str = "claude-haiku-4-5"
    # C9 — RabbitMQ queue ai-engine consumes for signal reasoning requests.
    # Defaults to the trading-core publisher's queue name; override in tests.
    reasoning_request_queue: str = "trading-core.signal.reasoning.requested"

    def parsed_cors_allowed_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_allowed_origins.split(",") if origin.strip()]


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings

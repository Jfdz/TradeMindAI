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

    def parsed_cors_allowed_origins(self) -> list[str]:
        return [origin.strip() for origin in self.cors_allowed_origins.split(",") if origin.strip()]


_settings: Settings | None = None


def get_settings() -> Settings:
    global _settings
    if _settings is None:
        _settings = Settings()
    return _settings

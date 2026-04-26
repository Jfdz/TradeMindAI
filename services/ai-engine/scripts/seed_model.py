"""Bootstrap a randomly-initialised StockCNN artifact so /ready returns 200.

Run once on first deploy before starting the ai-engine container:

    cd services/ai-engine
    python scripts/seed_model.py [--model-path ./models]
"""

import argparse
import sys
from pathlib import Path

# Allow running from the repo root or from services/ai-engine
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from ai_engine.core.models.cnn import StockCNN
from ai_engine.core.use_cases.model_registry import ModelRegistry


def main() -> None:
    parser = argparse.ArgumentParser(description="Seed a random-weight model artifact")
    parser.add_argument("--model-path", default="./models", help="Path to model registry directory")
    args = parser.parse_args()

    registry = ModelRegistry(args.model_path)

    if registry.active_version_id() is not None:
        print(f"Active model already exists: {registry.active_version_id()} — skipping seed.")
        return

    model = StockCNN()
    version_id = registry.save(
        model,
        version_tag="seed-v0",
        architecture="StockCNN",
        hyperparameters={"num_features": 17, "num_classes": 3},
        metrics={"note": "random-init seed — replace with trained model"},
    )
    registry.activate(version_id)
    print(f"Seed model saved and activated: {version_id}")
    print(f"Registry path: {Path(args.model_path).resolve()}")


if __name__ == "__main__":
    main()

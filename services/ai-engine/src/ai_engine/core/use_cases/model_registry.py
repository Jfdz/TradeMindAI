import json
import uuid
from datetime import datetime, timezone
from pathlib import Path

import torch
import torch.nn as nn


class ModelRegistry:
    """Save and load model artifacts with versioned metadata.

    Artifacts are stored under *base_path*/<version_id>/:
      - model.pt        — state_dict
      - metadata.json   — version tag, architecture, metrics, hyperparams
      - active          — symlink/marker pointing to the active version dir

    This implementation is filesystem-based; the Alembic DB records
    (model_versions table) are written by the adapter layer.
    """

    def __init__(self, base_path: str = "./models"):
        self._base = Path(base_path)
        self._base.mkdir(parents=True, exist_ok=True)
        self._active_marker = self._base / "active"

    # ── save ─────────────────────────────────────────────────────────────────

    def save(
        self,
        model: nn.Module,
        version_tag: str,
        architecture: str,
        hyperparameters: dict,
        metrics: dict,
    ) -> str:
        """Persist *model* weights and metadata. Returns the version_id (UUID)."""
        version_id = str(uuid.uuid4())
        version_dir = self._base / version_id
        version_dir.mkdir(parents=True)

        torch.save(model.state_dict(), version_dir / "model.pt")

        metadata = {
            "version_id": version_id,
            "version_tag": version_tag,
            "architecture": architecture,
            "hyperparameters": hyperparameters,
            "metrics": metrics,
            "saved_at": datetime.now(timezone.utc).isoformat(),
            "is_active": False,
        }
        (version_dir / "metadata.json").write_text(json.dumps(metadata, indent=2))
        return version_id

    # ── load ─────────────────────────────────────────────────────────────────

    def load(self, model: nn.Module, version_id: str) -> nn.Module:
        """Load weights for *version_id* into *model* in-place."""
        weights_path = self._base / version_id / "model.pt"
        if not weights_path.exists():
            raise FileNotFoundError(f"No artifact for version {version_id}")
        model.load_state_dict(torch.load(weights_path, map_location="cpu", weights_only=True))
        return model

    def load_active(self, model: nn.Module) -> nn.Module:
        """Load the currently active model version."""
        version_id = self._read_active()
        return self.load(model, version_id)

    # ── version management ────────────────────────────────────────────────────

    def activate(self, version_id: str) -> None:
        """Mark *version_id* as the active version."""
        if not (self._base / version_id).exists():
            raise FileNotFoundError(f"Version {version_id} not found.")
        self._active_marker.write_text(version_id)

        for vid in self.list_versions():
            meta_path = self._base / vid / "metadata.json"
            meta = json.loads(meta_path.read_text())
            meta["is_active"] = vid == version_id
            meta_path.write_text(json.dumps(meta, indent=2))

    def list_versions(self) -> list[str]:
        return [p.name for p in self._base.iterdir() if p.is_dir()]

    def get_metadata(self, version_id: str) -> dict:
        path = self._base / version_id / "metadata.json"
        if not path.exists():
            raise FileNotFoundError(f"Metadata not found for {version_id}")
        return json.loads(path.read_text())

    def active_version_id(self) -> str | None:
        return self._read_active() if self._active_marker.exists() else None

    # ── internal ─────────────────────────────────────────────────────────────

    def _read_active(self) -> str:
        if not self._active_marker.exists():
            raise RuntimeError("No active model version set.")
        return self._active_marker.read_text().strip()

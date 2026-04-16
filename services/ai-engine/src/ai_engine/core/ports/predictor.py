from abc import ABC, abstractmethod

import numpy as np
import torch


class BasePredictor(ABC):
    """Abstract interface all predictor implementations must satisfy."""

    @abstractmethod
    def load_model(self, path: str) -> None:
        """Load model weights from *path* into memory."""

    @abstractmethod
    def preprocess(self, data: np.ndarray) -> torch.Tensor:
        """Transform raw feature matrix into a model-ready tensor."""

    @abstractmethod
    def predict(self, data: np.ndarray) -> dict:
        """Run inference and return direction, confidence, and raw logits."""

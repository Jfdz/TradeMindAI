from dataclasses import dataclass

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader


@dataclass
class EvalMetrics:
    accuracy: float
    precision: dict[str, float]
    recall: dict[str, float]
    f1: dict[str, float]
    confusion_matrix: list[list[int]]
    class_names: list[str]


_CLASS_NAMES = ["DOWN", "NEUTRAL", "UP"]


class Evaluator:
    """Compute accuracy, per-class precision/recall/F1, and confusion matrix."""

    def __init__(self, model: nn.Module, device: str = "cpu"):
        self.model = model
        self.device = torch.device(device)

    def evaluate(self, loader: DataLoader) -> EvalMetrics:
        self.model.eval()
        all_preds: list[int] = []
        all_labels: list[int] = []

        with torch.no_grad():
            for x, y in loader:
                x = x.to(self.device)
                preds = self.model(x).argmax(dim=1).cpu().tolist()
                all_preds.extend(preds)
                all_labels.extend(y.tolist())

        return _compute_metrics(np.array(all_labels), np.array(all_preds))


def _compute_metrics(y_true: np.ndarray, y_pred: np.ndarray) -> EvalMetrics:
    num_classes = len(_CLASS_NAMES)
    cm = [[0] * num_classes for _ in range(num_classes)]
    for t, p in zip(y_true, y_pred):
        cm[t][p] += 1

    precision: dict[str, float] = {}
    recall: dict[str, float] = {}
    f1: dict[str, float] = {}

    for c, name in enumerate(_CLASS_NAMES):
        tp = cm[c][c]
        fp = sum(cm[r][c] for r in range(num_classes)) - tp
        fn = sum(cm[c][r] for r in range(num_classes)) - tp

        p = tp / (tp + fp) if (tp + fp) > 0 else 0.0
        r = tp / (tp + fn) if (tp + fn) > 0 else 0.0
        precision[name] = round(p, 4)
        recall[name] = round(r, 4)
        f1[name] = round(2 * p * r / (p + r) if (p + r) > 0 else 0.0, 4)

    accuracy = float(np.sum(y_true == y_pred) / len(y_true))

    return EvalMetrics(
        accuracy=round(accuracy, 4),
        precision=precision,
        recall=recall,
        f1=f1,
        confusion_matrix=cm,
        class_names=_CLASS_NAMES,
    )

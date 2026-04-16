from dataclasses import dataclass, field

import torch
import torch.nn as nn
from torch.optim import AdamW
from torch.optim.lr_scheduler import ReduceLROnPlateau
from torch.utils.data import DataLoader


@dataclass
class EpochMetrics:
    loss: float
    accuracy: float


@dataclass
class TrainResult:
    train_history: list[EpochMetrics] = field(default_factory=list)
    val_history: list[EpochMetrics] = field(default_factory=list)
    best_epoch: int = 0
    stopped_early: bool = False


class Trainer:
    """Training loop for StockCNN with early stopping.

    Args:
        model:          nn.Module to train.
        train_loader:   DataLoader for training split.
        val_loader:     DataLoader for validation split.
        lr:             Initial learning rate for AdamW.
        weight_decay:   L2 regularisation weight.
        class_weights:  Optional tensor of shape (num_classes,) for imbalanced data.
        patience:       Early stopping patience in epochs (default 10).
        max_epochs:     Maximum number of training epochs.
        device:         Torch device string (default "cpu").
    """

    def __init__(
        self,
        model: nn.Module,
        train_loader: DataLoader,
        val_loader: DataLoader,
        lr: float = 1e-3,
        weight_decay: float = 1e-4,
        class_weights: torch.Tensor | None = None,
        patience: int = 10,
        max_epochs: int = 100,
        device: str = "cpu",
    ):
        self.device = torch.device(device)
        self.model = model.to(self.device)
        self.train_loader = train_loader
        self.val_loader = val_loader
        self.patience = patience
        self.max_epochs = max_epochs

        weights = class_weights.to(self.device) if class_weights is not None else None
        self.criterion = nn.CrossEntropyLoss(weight=weights)
        self.optimizer = AdamW(model.parameters(), lr=lr, weight_decay=weight_decay)
        self.scheduler = ReduceLROnPlateau(self.optimizer, mode="min", patience=5, factor=0.5)

    def train(self) -> TrainResult:
        result = TrainResult()
        best_val_loss = float("inf")
        no_improve = 0

        for epoch in range(self.max_epochs):
            train_metrics = self._run_epoch(self.train_loader, train=True)
            val_metrics = self._run_epoch(self.val_loader, train=False)

            result.train_history.append(train_metrics)
            result.val_history.append(val_metrics)
            self.scheduler.step(val_metrics.loss)

            if val_metrics.loss < best_val_loss:
                best_val_loss = val_metrics.loss
                result.best_epoch = epoch
                no_improve = 0
            else:
                no_improve += 1
                if no_improve >= self.patience:
                    result.stopped_early = True
                    break

        return result

    def _run_epoch(self, loader: DataLoader, train: bool) -> EpochMetrics:
        self.model.train(train)
        total_loss = 0.0
        correct = 0
        total = 0

        ctx = torch.enable_grad() if train else torch.no_grad()
        with ctx:
            for X, y in loader:
                X, y = X.to(self.device), y.to(self.device)
                logits = self.model(X)
                loss = self.criterion(logits, y)

                if train:
                    self.optimizer.zero_grad()
                    loss.backward()
                    self.optimizer.step()

                total_loss += loss.item() * len(y)
                correct += (logits.argmax(dim=1) == y).sum().item()
                total += len(y)

        return EpochMetrics(loss=total_loss / total, accuracy=correct / total)

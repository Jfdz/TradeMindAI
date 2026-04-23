from dataclasses import dataclass

import numpy as np
import torch
from torch.utils.data import DataLoader, TensorDataset


@dataclass
class SplitDataLoaders:
    train: DataLoader
    val: DataLoader
    test: DataLoader
    train_size: int
    val_size: int
    test_size: int


def make_data_loaders(
    sequences: np.ndarray,
    labels: np.ndarray,
    batch_size: int = 64,
    train_ratio: float = 0.70,
    val_ratio: float = 0.15,
    num_workers: int = 0,
) -> SplitDataLoaders:
    """Temporal train/val/test split — no shuffling to prevent data leakage.

    Args:
        sequences: Shape (N_samples, n_features, window).
        labels:    Shape (N_samples,) — integer class indices.
        batch_size: Samples per batch.
        train_ratio: Fraction for training (default 0.70).
        val_ratio:   Fraction for validation (default 0.15).
                     test_ratio = 1 - train_ratio - val_ratio = 0.15.

    Returns:
        SplitDataLoaders with train, val, test DataLoaders.
    """
    n = len(sequences)
    train_end = int(n * train_ratio)
    val_end = train_end + int(n * val_ratio)

    x = torch.tensor(sequences, dtype=torch.float32)
    y = torch.tensor(labels, dtype=torch.long)

    def _loader(x_slice, y_slice, shuffle: bool) -> DataLoader:
        return DataLoader(
            TensorDataset(x_slice, y_slice),
            batch_size=batch_size,
            shuffle=shuffle,
            num_workers=num_workers,
            drop_last=False,
        )

    return SplitDataLoaders(
        train=_loader(x[:train_end], y[:train_end], shuffle=True),
        val=_loader(x[train_end:val_end], y[train_end:val_end], shuffle=False),
        test=_loader(x[val_end:], y[val_end:], shuffle=False),
        train_size=train_end,
        val_size=val_end - train_end,
        test_size=n - val_end,
    )

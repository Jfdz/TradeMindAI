import torch
import torch.nn as nn


class _ConvBlock(nn.Module):
    def __init__(self, in_channels: int, out_channels: int):
        super().__init__()
        self.block = nn.Sequential(
            nn.Conv1d(in_channels, out_channels, kernel_size=3, padding=1),
            nn.BatchNorm1d(out_channels),
            nn.ReLU(inplace=True),
            nn.MaxPool1d(kernel_size=2),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.block(x)


class StockCNN(nn.Module):
    """3-block 1D CNN for stock price direction classification.

    Input:  (batch, num_features=17, seq_len=60)
    Output: (batch, num_classes=3)  — logits for [DOWN, NEUTRAL, UP]
    """

    def __init__(self, num_features: int = 17, num_classes: int = 3):
        super().__init__()
        self.conv_blocks = nn.Sequential(
            _ConvBlock(num_features, 64),
            _ConvBlock(64, 128),
            _ConvBlock(128, 256),
        )
        self.pool = nn.AdaptiveAvgPool1d(1)
        self.classifier = nn.Sequential(
            nn.Dropout(0.5),
            nn.Linear(256, 128),
            nn.ReLU(inplace=True),
            nn.Linear(128, num_classes),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        x = self.conv_blocks(x)
        x = self.pool(x)
        x = x.squeeze(-1)
        return self.classifier(x)

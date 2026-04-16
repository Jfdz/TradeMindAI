import pytest
import torch

from ai_engine.core.models import StockCNN


@pytest.fixture
def model():
    return StockCNN()


def test_output_shape_default(model):
    x = torch.randn(8, 17, 60)
    out = model(x)
    assert out.shape == (8, 3)


def test_output_shape_batch_size_1(model):
    x = torch.randn(1, 17, 60)
    assert model(x).shape == (1, 3)


def test_output_shape_large_batch(model):
    x = torch.randn(64, 17, 60)
    assert model(x).shape == (64, 3)


def test_num_classes_configurable():
    m = StockCNN(num_classes=5)
    x = torch.randn(4, 17, 60)
    assert m(x).shape == (4, 5)


def test_num_features_configurable():
    m = StockCNN(num_features=10, num_classes=3)
    x = torch.randn(4, 10, 60)
    assert m(x).shape == (4, 3)


def test_gradient_flow(model):
    x = torch.randn(4, 17, 60, requires_grad=False)
    out = model(x)
    loss = out.sum()
    loss.backward()
    for name, param in model.named_parameters():
        assert param.grad is not None, f"No gradient for {name}"


def test_output_is_logits(model):
    model.eval()
    with torch.no_grad():
        x = torch.randn(4, 17, 60)
        out = model(x)
    # logits — not softmax-normalised, so rows do NOT necessarily sum to 1
    assert not torch.allclose(out.softmax(dim=-1).sum(dim=-1), torch.zeros(4))

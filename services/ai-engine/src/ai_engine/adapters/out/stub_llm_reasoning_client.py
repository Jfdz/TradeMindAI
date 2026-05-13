"""StubLlmReasoningClient — no-network default.

Returns REFUSED_LLM_DISABLED for every call so C5 validator and C6
persistence treat the signal as ungrounded and fall back to the
deterministic template in the UI ("Auto-summary" badge).

Active when settings.llm_provider == "stub" (the default) or when the
selected provider's credential is missing.
"""

from __future__ import annotations

import logging

from ai_engine.core.domain.reasoning_context import ReasoningContext
from ai_engine.core.domain.reasoning_output import (
    ReasoningResult,
    SignalInput,
)

logger = logging.getLogger(__name__)


class StubLlmReasoningClient:
    """LlmReasoningPort impl that always returns REFUSED_LLM_DISABLED."""

    PROVIDER_NAME = "stub"

    def generate(
        self,
        signal: SignalInput,
        context: ReasoningContext,
        validator_feedback: str | None = None,
    ) -> ReasoningResult:
        # validator_feedback is accepted to satisfy the LlmReasoningPort
        # signature; the stub does not call any LLM, so feedback has no
        # effect on the outcome.
        del validator_feedback
        logger.info(
            "event=llm_reasoning.stub_refusal ticker=%s confidence=%.2f news_count=%d",
            signal.ticker,
            signal.confidence,
            len(context.news),
        )
        return ReasoningResult.refused_llm_disabled()

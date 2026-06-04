"""Fase 3 orchestrator: grounded multi-agent deep analysis (no langgraph).

Reuses the deterministic `BuildReasoningContextUseCase` for facts, then runs a
minimal debate over the `ChatLlmPort`:

    BULL  ─┐
    BEAR  ─┼─► JUDGE ─► (verdict + direction)
    RISK  ─┘

Each advocacy section is grounded and validated with the existing
`ReasoningValidator` (the low-confidence-label rule is dropped — it is a UX
rule for the single grounded reasoning, not an anti-hallucination rule). A
section that fails grounding has its text withheld so nothing ungrounded
surfaces. The judge's verdict *direction* is structured (enum), so it survives
even when its prose rationale is withheld.

Outcomes:
  - context not AVAILABLE        -> REFUSED_NO_FACTS
  - judge yields no usable verdict-> ERROR (analysis has no headline)
  - all sections clean           -> GENERATED
  - a verdict stands but >=1 section refused/withheld -> PARTIAL

Never raises. Determinism is intentionally NOT asserted for this mode.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Callable

from ai_engine.core.domain.chat_llm import ChatLlmPort
from ai_engine.core.domain.deep_analysis import (
    DEEP_ANALYSIS_SCHEMA_VERSION,
    AnalysisRole,
    AnalysisSection,
    DeepAnalysis,
    DeepAnalysisResult,
    VerdictDirection,
    compute_conviction,
    parse_verdict_direction,
)
from ai_engine.core.domain.deep_analysis_prompts import (
    JUDGE_SYSTEM_PROMPT,
    OPINION_TOOL_SCHEMA,
    VERDICT_TOOL_SCHEMA,
    build_judge_prompt,
    build_role_prompt,
    role_system_prompt,
)
from ai_engine.core.domain.reasoning_context import ContextOutcome, ReasoningContext
from ai_engine.core.domain.reasoning_output import ReasoningPayload, SignalInput
from ai_engine.core.domain.reasoning_validation import (
    ReasoningValidator,
    ValidationViolationType,
)
from ai_engine.core.use_cases.build_reasoning_context import (
    BuildReasoningContextUseCase,
)

logger = logging.getLogger(__name__)

# The low-confidence-label rule is a UX rule for the single grounded reasoning,
# not an anti-hallucination rule — advocacy sections must not be forced to call
# themselves tentative. Every other validator rule still applies.
_SECTION_IGNORED_VIOLATIONS = frozenset({ValidationViolationType.MISSING_LOW_CONFIDENCE_LABEL})


class GenerateDeepAnalysisUseCase:
    """Grounded bull/bear/judge/risk debate over a single signal."""

    def __init__(
        self,
        context_use_case: BuildReasoningContextUseCase,
        chat_llm: ChatLlmPort,
        validator: ReasoningValidator,
        provider: str,
        model_version: str,
        clock: Callable[[], datetime] | None = None,
    ):
        self._context_use_case = context_use_case
        self._chat = chat_llm
        self._validator = validator
        self._provider = provider
        self._model_version = model_version
        self._now = clock or (lambda: datetime.now(tz=timezone.utc))

    def execute(self, signal: SignalInput) -> DeepAnalysisResult:
        context_result = self._context_use_case.execute(signal.ticker)
        if context_result.outcome != ContextOutcome.AVAILABLE:
            logger.info(
                "event=deep_analysis.refused_no_facts ticker=%s context_outcome=%s",
                signal.ticker,
                context_result.outcome.value,
            )
            return DeepAnalysisResult.refused_no_facts(
                f"context_outcome={context_result.outcome.value}"
            )

        assert context_result.context is not None  # invariant of AVAILABLE
        ctx = context_result.context

        bull = self._run_role(AnalysisRole.BULL, signal, ctx)
        bear = self._run_role(AnalysisRole.BEAR, signal, ctx)

        judged = self._run_judge(signal, ctx, bull, bear)
        if judged is None:
            logger.warning("event=deep_analysis.no_verdict ticker=%s", signal.ticker)
            return DeepAnalysisResult.error("judge_produced_no_verdict")
        verdict_section, direction = judged

        risk = self._run_role(AnalysisRole.RISK, signal, ctx)

        conviction = compute_conviction(signal.signal_type, direction)
        sections = (bull, bear, risk)
        any_withheld = verdict_section.refused or any(s.refused for s in sections)

        analysis = DeepAnalysis(
            schema_version=DEEP_ANALYSIS_SCHEMA_VERSION,
            ticker=ctx.ticker,
            signal_type=signal.signal_type,
            generated_at=self._now(),
            sections=sections,
            verdict=verdict_section,
            verdict_direction=direction,
            conviction=conviction,
            provider=self._provider,
            model_version=self._model_version,
        )

        logger.info(
            "event=deep_analysis.completed ticker=%s verdict=%s conviction=%s " "withheld=%s",
            signal.ticker,
            direction.value,
            conviction.value,
            any_withheld,
        )
        if any_withheld:
            return DeepAnalysisResult.partial(analysis)
        return DeepAnalysisResult.generated(analysis)

    # --- roles ---

    def _run_role(
        self, role: AnalysisRole, signal: SignalInput, context: ReasoningContext
    ) -> AnalysisSection:
        result = self._chat.complete_tool(
            role_system_prompt(role),
            build_role_prompt(role, signal, context),
            OPINION_TOOL_SCHEMA,
        )
        if not result.ok:
            return AnalysisSection(
                role=role, text="", refused=True, refusal_reason=result.error or "chat_failed"
            )

        args = result.arguments or {}
        if args.get("refusal") is True:
            return AnalysisSection(
                role=role,
                text="",
                refused=True,
                refusal_reason=args.get("refusal_reason") or "model_refused",
            )

        text = (args.get("opinion") or "").strip()
        if not text:
            return AnalysisSection(role=role, text="", refused=True, refusal_reason="empty_opinion")

        price_refs = tuple(args.get("price_refs") or ())
        news_refs = tuple(args.get("news_refs") or ())
        violations = self._grounding_violations(text, news_refs, signal, context)
        if violations:
            logger.warning(
                "event=deep_analysis.section_withheld role=%s ticker=%s violations=%d",
                role.value,
                signal.ticker,
                len(violations),
            )
            return AnalysisSection(
                role=role,
                text="",
                refused=True,
                refusal_reason="failed_grounding_validation",
                validator_violations=violations,
            )
        return AnalysisSection(role=role, text=text, price_refs=price_refs, news_refs=news_refs)

    def _run_judge(
        self,
        signal: SignalInput,
        context: ReasoningContext,
        bull: AnalysisSection,
        bear: AnalysisSection,
    ) -> tuple[AnalysisSection, VerdictDirection] | None:
        result = self._chat.complete_tool(
            JUDGE_SYSTEM_PROMPT,
            build_judge_prompt(signal, context, bull.text, bear.text),
            VERDICT_TOOL_SCHEMA,
        )
        if not result.ok:
            return None

        args = result.arguments or {}
        direction = parse_verdict_direction(args.get("verdict"))
        if direction is None:
            return None

        rationale = (args.get("rationale") or "").strip()
        price_refs = tuple(args.get("price_refs") or ())
        news_refs = tuple(args.get("news_refs") or ())

        if rationale:
            violations = self._grounding_violations(rationale, news_refs, signal, context)
            if violations:
                logger.warning(
                    "event=deep_analysis.verdict_rationale_withheld ticker=%s " "violations=%d",
                    signal.ticker,
                    len(violations),
                )
                # Keep the structured direction; withhold the ungrounded prose.
                return (
                    AnalysisSection(
                        role=AnalysisRole.JUDGE,
                        text="",
                        refused=True,
                        refusal_reason="failed_grounding_validation",
                        validator_violations=violations,
                    ),
                    direction,
                )

        return (
            AnalysisSection(
                role=AnalysisRole.JUDGE,
                text=rationale,
                price_refs=price_refs,
                news_refs=news_refs,
            ),
            direction,
        )

    def _grounding_violations(
        self,
        text: str,
        news_refs: tuple[str, ...],
        signal: SignalInput,
        context: ReasoningContext,
    ) -> tuple[dict, ...]:
        payload = ReasoningPayload(text=text, price_refs=(), news_refs=news_refs)
        validation = self._validator.validate(payload, signal, context)
        return tuple(
            {"type": v.type.value, "detail": v.detail}
            for v in validation.violations
            if v.type not in _SECTION_IGNORED_VIOLATIONS
        )

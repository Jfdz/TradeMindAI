"""Internal compute endpoint for the Fase 3 deep-analysis debate.

Stateless edge: this route owns no persistence. trading-core orchestrates
(tier check -> call here -> persist -> return); ai-engine only computes the
grounded multi-agent analysis and hands it back. Guarded by X-Internal-Secret
like every other `/api/v1/internal/**` route; the web app never calls it.

Outcome -> HTTP mapping (RFC-7807-ish, never "200 success:false"):
  GENERATED / PARTIAL  -> 200 with the artifact (outcome distinguishes them)
  REFUSED_NO_FACTS     -> 422 (no grounded facts to debate — do not retry)
  ERROR                -> 502 (the debate produced no verdict — may be transient)
"""

from __future__ import annotations

from datetime import datetime, timezone

from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel, ConfigDict, Field

from ai_engine.adapters.in_.auth import require_internal_secret
from ai_engine.core.domain.deep_analysis import (
    AnalysisSection,
    DeepAnalysis,
    DeepAnalysisOutcome,
)
from ai_engine.core.domain.reasoning_output import SignalInput

router = APIRouter(prefix="/api/v1/internal", tags=["deep-analysis"])


# ── request / response schemas (camelCase wire shape for the Java client) ──────


class DeepAnalysisRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    signal_type: str = Field(alias="signalType")
    confidence: float = 0.0
    entry_price: float = Field(default=0.0, alias="entryPrice")
    predicted_change_pct: float | None = Field(default=None, alias="predictedChangePct")
    generated_at: datetime | None = Field(default=None, alias="generatedAt")
    target_price: float | None = Field(default=None, alias="targetPrice")
    stop_loss: float | None = Field(default=None, alias="stopLoss")
    expected_move_pct: float | None = Field(default=None, alias="expectedMovePct")


class AnalysisSectionResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    role: str
    text: str
    price_refs: list[str] = Field(default_factory=list, alias="priceRefs")
    news_refs: list[str] = Field(default_factory=list, alias="newsRefs")
    refused: bool = False
    refusal_reason: str | None = Field(default=None, alias="refusalReason")
    validator_violations: list[dict] = Field(default_factory=list, alias="validatorViolations")


class DeepAnalysisResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    schema_version: str = Field(alias="schemaVersion")
    outcome: str
    ticker: str
    signal_type: str = Field(alias="signalType")
    generated_at: datetime = Field(alias="generatedAt")
    verdict_direction: str = Field(alias="verdictDirection")
    conviction: str
    verdict: AnalysisSectionResponse
    sections: list[AnalysisSectionResponse]
    provider: str
    model_version: str = Field(alias="modelVersion")


# ── endpoint ───────────────────────────────────────────────────────────────────


@router.post(
    "/deep-analysis/{ticker}",
    response_model=DeepAnalysisResponse,
    dependencies=[Depends(require_internal_secret)],
)
async def generate_deep_analysis(ticker: str, body: DeepAnalysisRequest, request: Request):
    """Run the grounded bull/bear/judge/risk debate for one signal."""
    use_case = _get_use_case(request)
    signal = SignalInput(
        ticker=ticker.upper(),
        signal_type=body.signal_type,
        confidence=body.confidence,
        entry_price=body.entry_price,
        predicted_change_pct=body.predicted_change_pct,
        generated_at=body.generated_at or datetime.now(tz=timezone.utc),
        target_price=body.target_price,
        stop_loss=body.stop_loss,
        expected_move_pct=body.expected_move_pct,
    )

    result = use_case.execute(signal)

    if result.outcome in (DeepAnalysisOutcome.GENERATED, DeepAnalysisOutcome.PARTIAL):
        assert result.analysis is not None
        return _to_response(result.analysis, result.outcome)
    if result.outcome == DeepAnalysisOutcome.REFUSED_NO_FACTS:
        raise HTTPException(status_code=422, detail=result.detail or "no_grounded_facts")
    raise HTTPException(status_code=502, detail=result.detail or "deep_analysis_failed")


# ── helpers ───────────────────────────────────────────────────────────────────


def _get_use_case(request: Request):
    use_case = getattr(request.app.state, "deep_analysis_use_case", None)
    if use_case is None:
        from ai_engine.adapters.out.deep_analysis_factory import (
            create_deep_analysis_use_case,
        )
        from ai_engine.config import get_settings

        use_case = create_deep_analysis_use_case(get_settings())
        request.app.state.deep_analysis_use_case = use_case
    return use_case


def _section_response(section: AnalysisSection) -> AnalysisSectionResponse:
    return AnalysisSectionResponse(
        role=section.role.value,
        text=section.text,
        price_refs=list(section.price_refs),
        news_refs=list(section.news_refs),
        refused=section.refused,
        refusal_reason=section.refusal_reason,
        validator_violations=list(section.validator_violations),
    )


def _to_response(analysis: DeepAnalysis, outcome: DeepAnalysisOutcome) -> DeepAnalysisResponse:
    return DeepAnalysisResponse(
        schema_version=analysis.schema_version,
        outcome=outcome.value,
        ticker=analysis.ticker,
        signal_type=analysis.signal_type,
        generated_at=analysis.generated_at,
        verdict_direction=analysis.verdict_direction.value,
        conviction=analysis.conviction.value,
        verdict=_section_response(analysis.verdict),
        sections=[_section_response(s) for s in analysis.sections],
        provider=analysis.provider,
        model_version=analysis.model_version,
    )

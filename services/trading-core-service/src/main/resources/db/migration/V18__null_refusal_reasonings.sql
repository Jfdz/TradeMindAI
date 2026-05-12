-- Clear previously persisted LLM refusal strings so they get regenerated on
-- the next reasoning cycle. The patterns mirror the regexes in
-- LlmOutputValidator and target the phrasings observed in production.
-- Rows are reset to PENDING so the async reasoning listener will pick them up.

UPDATE trading_core.trading_signals
SET    reasoning              = NULL,
       reasoning_status       = 'PENDING',
       reasoning_generated_at = NULL
WHERE  reasoning IS NOT NULL
  AND  (
        reasoning ~* '\yI ?(can''?t|cannot|won''?t)\y.*\y(answer|provide|generate|give|help|write|create|make)\y'
     OR reasoning ~* '\yI''?m (unable|not able|sorry|afraid)\y'
     OR reasoning ~* '\y(investment|financial|trading) advice\y'
     OR reasoning ~* '\y(as an?|I am an?|I''?m an?) (AI|assistant|language model|LLM)\y'
     OR reasoning ~* '\ynot (a )?(licensed|qualified|certified|professional) (financial|investment)\y'
     OR reasoning ~* '\y(language model|large language model)\y'
  );

package com.tradingsaas.tradingcore.adapter.out.llm;

import java.util.List;
import java.util.Optional;

class LlmOutputValidator {

    private static final int MAX_CHARS = 280;
    private static final List<String> BANNED = List.of(
            "AI", "model", "not financial advice", "I am an", "I'm an", "language model");

    Optional<String> validate(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String trimmed = raw.strip();
        if (trimmed.length() > MAX_CHARS) return Optional.empty();
        for (String banned : BANNED) {
            if (raw.contains(banned)) return Optional.empty();
        }
        return Optional.of(trimmed);
    }
}

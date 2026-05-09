package com.tradingsaas.tradingcore.adapter.out.llm.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GroqResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {}

    public String extractText() {
        if (choices == null || choices.isEmpty()) return null;
        Choice c = choices.get(0);
        return c.message() != null ? c.message().content() : null;
    }
}

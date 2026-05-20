package com.tradingsaas.tradingcore.adapter.out.llm.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record GroqRequest(
        String model,
        List<Message> messages,
        @JsonProperty("max_tokens") int maxTokens,
        double temperature) {

    public record Message(String role, String content) {}

    public static GroqRequest of(String model, String prompt, int maxTokens, double temperature) {
        return new GroqRequest(model, List.of(new Message("user", prompt)), maxTokens, temperature);
    }
}

package com.tradingsaas.tradingcore.adapter.out.llm.dto;

import java.util.List;

public record GeminiRequest(List<Content> contents, GenerationConfig generationConfig) {

    public record Content(List<Part> parts) {}
    public record Part(String text) {}
    public record GenerationConfig(int maxOutputTokens, double temperature) {}

    public static GeminiRequest of(String prompt, int maxOutputTokens, double temperature) {
        return new GeminiRequest(
                List.of(new Content(List.of(new Part(prompt)))),
                new GenerationConfig(maxOutputTokens, temperature));
    }
}

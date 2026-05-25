package com.semantic.gateway.embedding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Service
public class OpenAIEmbeddingService {

        private final WebClient webClient;

        @Value("${openai.api.key}")
        private String apiKey;

        public OpenAIEmbeddingService(WebClient.Builder builder) {
                this.webClient = builder.baseUrl("https://api.openai.com").build();
        }

        // ✅ EMBEDDING
        @SuppressWarnings("unchecked")
        public List<Double> generateEmbedding(String text) {

                Map<String, Object> response = webClient.post()
                                .uri("/v1/embeddings")
                                .header("Authorization", "Bearer " + apiKey)
                                .bodyValue(Map.of(
                                                "model", "text-embedding-3-small",
                                                "input", text))
                                .retrieve()
                                .bodyToMono(Map.class)
                                .block();

                List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");

                return (List<Double>) data.get(0).get("embedding");
        }

        // ✅ LLM RESPONSE (THIS WAS MISSING)
        @SuppressWarnings("unchecked")
        public String generateResponse(String prompt) {

                Map<String, Object> response = webClient.post()
                                .uri("/v1/chat/completions")
                                .header("Authorization", "Bearer " + apiKey)
                                .bodyValue(Map.of(
                                                "model", "gpt-4o-mini",
                                                "max_tokens", 100, // 🔥 LIMIT SIZE
                                                "messages", List.of(
                                                                Map.of("role", "user", "content", prompt))))
                                .retrieve()
                                .bodyToMono(Map.class)
                                .block();

                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");

                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");

                return (String) message.get("content");
        }
}
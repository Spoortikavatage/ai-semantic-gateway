package com.semantic.gateway.service;

import com.semantic.gateway.embedding.EmbeddingService;
import com.semantic.gateway.model.QueryRequest;
import com.semantic.gateway.model.QueryResponse;
import com.semantic.gateway.model.SemanticCache;
import com.semantic.gateway.repository.SemanticCacheRepository;
import com.semantic.gateway.util.SimilarityUtil;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class QueryService {

    private final EmbeddingService embeddingService;
    private final SemanticCacheRepository repository;

    public QueryService(EmbeddingService embeddingService,
                        SemanticCacheRepository repository) {
        this.embeddingService = embeddingService;
        this.repository = repository;
    }

    public QueryResponse process(QueryRequest request) {

        String prompt = request.getPrompt();

        // Step 1: Generate embedding
        List<Double> newEmbedding = embeddingService.generateEmbedding(prompt);

        // Step 2: Fetch all stored data
        List<SemanticCache> allData = repository.findAll();

        double bestScore = 0.0;
        SemanticCache bestMatch = null;

        // Step 3: Compare embeddings
        for (SemanticCache data : allData) {

            String embStr = data.getEmbedding()
                    .replace("[", "")
                    .replace("]", "");

            List<Double> storedEmbedding = Arrays.stream(embStr.split(","))
                    .map(String::trim)
                    .map(Double::parseDouble)
                    .toList();

            double score = SimilarityUtil.cosineSimilarity(newEmbedding, storedEmbedding);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = data;
            }
        }

        // Step 4: Return cache if similar
        if (bestMatch != null && bestScore > 0.8) {
            return new QueryResponse(
                    bestMatch.getResponse(),
                    "cache",
                    bestScore
            );
        }

        // Step 5: Process normally
        String response = "Processed: " + prompt;

        // Step 6: Save to DB
        SemanticCache cache = new SemanticCache(
                prompt,
                response,
                newEmbedding.toString()
        );

        repository.save(cache);

        // Step 7: Return new response
        return new QueryResponse(
                response,
                "llm",
                null
        );
    }
}
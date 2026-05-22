package com.semantic.gateway.service;

import com.semantic.gateway.embedding.EmbeddingService;
import com.semantic.gateway.model.QueryRequest;
import com.semantic.gateway.model.QueryResponse;
import com.semantic.gateway.model.SemanticCache;
import com.semantic.gateway.repository.SemanticCacheRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryService {

  private final EmbeddingService embeddingService;
  private final SemanticCacheRepository repository;
  private final SimilarityService similarityService;

  public QueryService(EmbeddingService embeddingService,
      SemanticCacheRepository repository,
      SimilarityService similarityService) {
    this.embeddingService = embeddingService;
    this.repository = repository;
    this.similarityService = similarityService;
  }

  private List<Double> parseEmbedding(String embeddingStr) {

    embeddingStr = embeddingStr.replace("[", "").replace("]", "");
    String[] parts = embeddingStr.split(",");

    return java.util.Arrays.stream(parts)
        .map(String::trim)
        .map(Double::parseDouble)
        .toList();
  }

  public QueryResponse process(QueryRequest request) {

    String prompt = request.getPrompt();

    // Step 1: generate embedding
    List<Double> newEmbedding = embeddingService.generateEmbedding(prompt);

    // Step 2: fetch all stored data
    List<SemanticCache> allData = repository.findAll();

    double bestScore = 0.0;
    SemanticCache bestMatch = null;

    // Step 3: compare with all embeddings
    for (SemanticCache data : allData) {

      List<Double> storedEmbedding = parseEmbedding(data.getEmbedding());

      double score = similarityService.cosineSimilarity(newEmbedding, storedEmbedding);

      if (score > bestScore) {
        bestScore = score;
        bestMatch = data;
      }
    }

    // Step 4: threshold check
    double THRESHOLD = 0.8;

    if (bestMatch != null && bestScore > THRESHOLD) {

      return new QueryResponse(
          bestMatch.getResponse(),
          "cache",
          bestScore);
    }

    // Step 5: fallback (mock LLM)
    String responseText = "Processed: " + prompt;

    // Step 6: save new data
    SemanticCache cache = new SemanticCache(
        prompt,
        responseText,
        newEmbedding.toString());

    repository.save(cache);

    return new QueryResponse(
        responseText,
        "llm",
        null);
  }
}
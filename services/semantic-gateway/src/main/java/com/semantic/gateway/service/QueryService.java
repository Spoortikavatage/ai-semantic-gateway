package com.semantic.gateway.service;

import com.semantic.gateway.embedding.EmbeddingService;
import com.semantic.gateway.model.QueryRequest;
import com.semantic.gateway.model.QueryResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryService {

  private final EmbeddingService embeddingService;

  public QueryService(EmbeddingService embeddingService) {
    this.embeddingService = embeddingService;
  }

  public QueryResponse process(QueryRequest request) {

    String prompt = request.getPrompt();

    // Step 1: generate embedding
    List<Double> embedding = embeddingService.generateEmbedding(prompt);

    return new QueryResponse(
        "Processed: " + prompt,
        "mock",
        null);
  }
}
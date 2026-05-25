package com.semantic.gateway.service;

import com.semantic.gateway.embedding.OpenAIEmbeddingService;
import com.semantic.gateway.model.QueryRequest;
import com.semantic.gateway.model.QueryResponse;
import com.semantic.gateway.repository.SemanticCacheRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QueryService {

  private final OpenAIEmbeddingService embeddingService;
  private final SemanticCacheRepository repository;

  public QueryService(OpenAIEmbeddingService embeddingService,
      SemanticCacheRepository repository) {
    this.embeddingService = embeddingService;
    this.repository = repository;
  }

  public QueryResponse process(QueryRequest request) {

    String prompt = request.getPrompt();

    // Step 1: Embedding
    List<Double> newEmbedding = embeddingService.generateEmbedding(prompt);

    String vectorStr = formatVector(newEmbedding);

    // Step 2: Top-3 search
    List<Object[]> results = repository.findTopMatches(vectorStr);

    Object[] bestRow = null;
    double bestDistance = Double.MAX_VALUE;

    for (Object[] row : results) {

      String matchedPrompt = (String) row[1];
      double distance = ((Number) row[3]).doubleValue();

      System.out.println("INPUT: " + prompt);
      System.out.println("CHECKING: " + matchedPrompt);
      System.out.println("DISTANCE: " + distance);

      if (distance < bestDistance) {
        bestDistance = distance;
        bestRow = row;
      }
    }

    // Step 3: Cache logic
    if (bestRow != null) {

      String matchedPrompt = (String) bestRow[1];
      String matchedResponse = (String) bestRow[2];

      boolean isSemanticMatch = bestDistance < 0.85;
      boolean isTypoValid = bestDistance < 1.0 && isTypoMatch(prompt, matchedPrompt);

      if (isSemanticMatch || isTypoValid) {

        return new QueryResponse(
            matchedResponse, // 🔥 reuse cached answer
            "cache",
            1 - bestDistance);

        // return new QueryResponse(response, "cache", 1 - bestDistance);
      }
    }

    // Step 4: LLM fallback
    String response = embeddingService.generateResponse(
        "Answer briefly (1-2 lines): " + prompt);

    repository.saveWithVector(prompt, response, vectorStr);

    return new QueryResponse(response, "llm", null);
  }

  private String formatVector(List<Double> vector) {
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < vector.size(); i++) {
      if (i > 0)
        sb.append(",");
      sb.append(vector.get(i));
    }
    sb.append("]");
    return sb.toString();
  }

  private boolean isTypoMatch(String a, String b) {
    return levenshteinDistance(a, b) <= 2;
  }

  private int levenshteinDistance(String a, String b) {
    int[][] dp = new int[a.length() + 1][b.length() + 1];

    for (int i = 0; i <= a.length(); i++)
      dp[i][0] = i;
    for (int j = 0; j <= b.length(); j++)
      dp[0][j] = j;

    for (int i = 1; i <= a.length(); i++) {
      for (int j = 1; j <= b.length(); j++) {
        if (a.charAt(i - 1) == b.charAt(j - 1))
          dp[i][j] = dp[i - 1][j - 1];
        else
          dp[i][j] = 1 + Math.min(
              dp[i - 1][j - 1],
              Math.min(dp[i - 1][j], dp[i][j - 1]));
      }
    }

    return dp[a.length()][b.length()];
  }
}
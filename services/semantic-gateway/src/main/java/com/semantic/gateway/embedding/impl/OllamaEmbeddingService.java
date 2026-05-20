package com.semantic.gateway.embedding.impl;

import com.semantic.gateway.embedding.EmbeddingService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class OllamaEmbeddingService implements EmbeddingService {

  @Override
  public List<Double> generateEmbedding(String text) {

    List<Double> vector = new ArrayList<>();
    Random random = new Random();

    for (int i = 0; i < 5; i++) {
      vector.add(random.nextDouble());
    }

    return vector;
  }
}
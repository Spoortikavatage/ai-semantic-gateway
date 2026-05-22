package com.semantic.gateway.model;

import jakarta.persistence.*;

@Entity
public class SemanticCache {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String prompt;

  @Column(columnDefinition = "TEXT")
  private String response;

  @Column(columnDefinition = "TEXT")
  private String embedding;

  public SemanticCache() {
  }

  public SemanticCache(String prompt, String response, String embedding) {
    this.prompt = prompt;
    this.response = response;
    this.embedding = embedding;
  }

  public Long getId() {
    return id;
  }

  public String getPrompt() {
    return prompt;
  }

  public String getResponse() {
    return response;
  }

  public String getEmbedding() {
    return embedding;
  }
}
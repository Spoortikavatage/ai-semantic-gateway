package com.semantic.gateway.model;

public class QueryResponse {

  private String response;
  private String source;
  private Double similarity;

  public QueryResponse(String response, String source, Double similarity) {
    this.response = response;
    this.source = source;
    this.similarity = similarity;
  }

  public String getResponse() {
    return response;
  }

  public String getSource() {
    return source;
  }

  public Double getSimilarity() {
    return similarity;
  }
}
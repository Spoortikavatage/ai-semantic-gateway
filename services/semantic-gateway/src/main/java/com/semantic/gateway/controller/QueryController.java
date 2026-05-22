package com.semantic.gateway.controller;
import com.semantic.gateway.model.QueryRequest;
import com.semantic.gateway.model.QueryResponse;
import com.semantic.gateway.service.QueryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class QueryController {

  private final QueryService queryService;

  public QueryController(QueryService queryService) {
    this.queryService = queryService;
  }

  @PostMapping("/query")
  public QueryResponse query(@RequestBody QueryRequest request) {
    return queryService.process(request);
  }

  // Optional (to avoid 404 on browser)
  @GetMapping("/")
  public String home() {
    return "Semantic Gateway is running";
  }
}
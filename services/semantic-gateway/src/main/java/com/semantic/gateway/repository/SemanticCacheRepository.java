package com.semantic.gateway.repository;

import com.semantic.gateway.model.SemanticCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SemanticCacheRepository extends JpaRepository<SemanticCache, Long> {
}
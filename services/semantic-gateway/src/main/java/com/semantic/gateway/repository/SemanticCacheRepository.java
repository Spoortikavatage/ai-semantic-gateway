package com.semantic.gateway.repository;

import com.semantic.gateway.model.SemanticCache;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface SemanticCacheRepository extends JpaRepository<SemanticCache, Long> {

    // 🔥 TOP-3 MATCHES
    @Query(value = """
                SELECT id, prompt, response,
                       (embedding <-> CAST(:embedding AS vector)) as distance
                FROM semantic_cache
                ORDER BY embedding <-> CAST(:embedding AS vector)
                LIMIT 3
            """, nativeQuery = true)
    List<Object[]> findTopMatches(@Param("embedding") String embedding);

    // ✅ SAVE WITH VECTOR
    @Modifying
    @Transactional
    @Query(value = """
                INSERT INTO semantic_cache (prompt, response, embedding)
                VALUES (:prompt, :response, CAST(:embedding AS vector))
            """, nativeQuery = true)
    void saveWithVector(
            @Param("prompt") String prompt,
            @Param("response") String response,
            @Param("embedding") String embedding);
}
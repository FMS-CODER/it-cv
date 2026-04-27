package com.quanxiaoha.ai.robot.repository;

import com.quanxiaoha.ai.robot.entity.ResumeKnowledgeBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeKnowledgeBaseRepository extends JpaRepository<ResumeKnowledgeBase, Long> {

    List<ResumeKnowledgeBase> findByCategory(String category);

    @Query(value = "SELECT * FROM resume_knowledge_base " +
            "ORDER BY embedding <-> CAST(:embedding AS vector) " +
            "LIMIT :limit", nativeQuery = true)
    List<ResumeKnowledgeBase> findSimilarByEmbedding(@Param("embedding") float[] embedding, @Param("limit") int limit);

    @Query(value = "SELECT * FROM resume_knowledge_base " +
            "WHERE category = :category " +
            "ORDER BY embedding <-> CAST(:embedding AS vector) " +
            "LIMIT :limit", nativeQuery = true)
    List<ResumeKnowledgeBase> findSimilarByCategoryAndEmbedding(
            @Param("category") String category,
            @Param("embedding") float[] embedding,
            @Param("limit") int limit);
}

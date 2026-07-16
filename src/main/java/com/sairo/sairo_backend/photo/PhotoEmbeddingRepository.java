package com.sairo.sairo_backend.photo;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Repository
@RequiredArgsConstructor
public class PhotoEmbeddingRepository {

    private final JdbcTemplate jdbcTemplate;

    public Map<String, float[]> findEmbeddingsByIds(List<String> ids) {
        Map<String, float[]> result = new HashMap<>();
        if (ids.isEmpty()) return result;

        jdbcTemplate.query(con -> {
            Array arr = con.createArrayOf("text", ids.toArray());
            var ps = con.prepareStatement(
                    "SELECT id, embedding::text FROM photos WHERE id = ANY(?)");
            ps.setArray(1, arr);
            return ps;
        }, rs -> {
            result.put(rs.getString("id"), parseVector(rs.getString("embedding")));
        });

        return result;
    }

    public record SimilarPhoto(String location, String keywords) {}

    public List<SimilarPhoto> findSimilarPhotos(float[] queryEmbedding, int limit) {
        String vecStr = toVectorString(queryEmbedding);
        return jdbcTemplate.query(
                "SELECT location, keywords FROM photos WHERE location IS NOT NULL " +
                "ORDER BY embedding <=> ?::vector LIMIT ?",
                (rs, rowNum) -> new SimilarPhoto(rs.getString("location"), rs.getString("keywords")),
                vecStr, limit
        );
    }

    private float[] parseVector(String vecStr) {
        if (vecStr == null || vecStr.isBlank()) return new float[0];
        String inner = vecStr.replaceAll("[\\[\\]]", "").trim();
        String[] parts = inner.split(",");
        float[] result = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = Float.parseFloat(parts[i].trim());
        }
        return result;
    }

    static String toVectorString(float[] embedding) {
        return "[" + IntStream.range(0, embedding.length)
                .mapToObj(i -> String.valueOf(embedding[i]))
                .collect(Collectors.joining(",")) + "]";
    }
}

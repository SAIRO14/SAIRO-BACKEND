package com.sairo.sairo_backend.analysis;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnalysisStore {

    public record AnalysisEntry(float[] embedding, List<String> moodTags) {}

    private final ConcurrentHashMap<String, AnalysisEntry> store = new ConcurrentHashMap<>();

    public String save(float[] embedding, List<String> moodTags) {
        String id = UUID.randomUUID().toString();
        store.put(id, new AnalysisEntry(embedding, moodTags));
        return id;
    }

    public Optional<AnalysisEntry> find(String analysisId) {
        return Optional.ofNullable(store.get(analysisId));
    }
}

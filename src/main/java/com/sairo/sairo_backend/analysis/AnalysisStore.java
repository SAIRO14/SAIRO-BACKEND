package com.sairo.sairo_backend.analysis;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AnalysisStore {

    private final ConcurrentHashMap<String, float[]> store = new ConcurrentHashMap<>();

    public String save(float[] embedding) {
        String id = UUID.randomUUID().toString();
        store.put(id, embedding);
        return id;
    }

    public Optional<float[]> find(String analysisId) {
        return Optional.ofNullable(store.get(analysisId));
    }
}

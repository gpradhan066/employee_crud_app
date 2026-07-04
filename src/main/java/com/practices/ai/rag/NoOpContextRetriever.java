package com.practices.ai.rag;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class NoOpContextRetriever implements ContextRetriever {
    @Override public List<String> retrieve(String query) { return List.of(); }
}

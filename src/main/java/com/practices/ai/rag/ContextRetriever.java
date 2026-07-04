package com.practices.ai.rag;

import java.util.List;

public interface ContextRetriever {
    List<String> retrieve(String query);
}

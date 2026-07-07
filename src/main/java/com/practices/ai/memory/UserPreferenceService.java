package com.practices.ai.memory;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.practices.ai.memory.dto.UserPreferenceRequest;
import com.practices.ai.memory.dto.UserPreferenceResponse;
import com.practices.entity.UserPreference;
import com.practices.repository.UserPreferenceRepo;

@Service
public class UserPreferenceService {
    private final UserPreferenceRepo repo;

    public UserPreferenceService(UserPreferenceRepo repo) {
        this.repo = repo;
    }

    public UserPreferenceResponse get(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }
        return repo.findById(userId)
                .map(this::toResponse)
                .orElseGet(() -> new UserPreferenceResponse(userId, null, null, null, null));
    }

    public UserPreferenceResponse save(UserPreferenceRequest request) {
        if (request == null || request.userId() == null || request.userId().isBlank()) {
            throw new IllegalArgumentException("userId is required.");
        }
        UserPreference preference = repo.findById(request.userId()).orElseGet(UserPreference::new);
        preference.setUserId(request.userId());
        preference.setPreferredProvider(request.preferredProvider());
        preference.setPreferredModel(request.preferredModel());
        preference.setThinkingEnabled(request.thinkingEnabled());
        preference.setUpdatedAt(Instant.now());
        return toResponse(repo.save(preference));
    }

    private UserPreferenceResponse toResponse(UserPreference preference) {
        return new UserPreferenceResponse(
                preference.getUserId(),
                preference.getPreferredProvider(),
                preference.getPreferredModel(),
                preference.getThinkingEnabled(),
                preference.getUpdatedAt());
    }
}

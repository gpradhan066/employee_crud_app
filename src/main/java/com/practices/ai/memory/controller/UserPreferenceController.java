package com.practices.ai.memory.controller;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.practices.ai.memory.UserPreferenceService;
import com.practices.ai.memory.dto.UserPreferenceRequest;
import com.practices.ai.memory.dto.UserPreferenceResponse;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/preferences")
public class UserPreferenceController {
    private final UserPreferenceService preferenceService;

    public UserPreferenceController(UserPreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping
    public UserPreferenceResponse get(@RequestParam String userId) {
        return preferenceService.get(userId);
    }

    @PutMapping
    public UserPreferenceResponse save(@RequestBody UserPreferenceRequest request) {
        return preferenceService.save(request);
    }
}

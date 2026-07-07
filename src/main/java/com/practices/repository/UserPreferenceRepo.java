package com.practices.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.practices.entity.UserPreference;

public interface UserPreferenceRepo extends JpaRepository<UserPreference, String> {
}

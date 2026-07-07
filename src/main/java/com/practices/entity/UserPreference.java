package com.practices.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "user_preference")
@Data
public class UserPreference {

    @Id
    @Column(length = 64)
    private String userId;

    private String preferredProvider;
    private String preferredModel;
    private Boolean thinkingEnabled;

    @Column(nullable = false)
    private Instant updatedAt;
}

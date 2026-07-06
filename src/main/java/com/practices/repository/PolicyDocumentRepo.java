package com.practices.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.practices.entity.PolicyDocument;

public interface PolicyDocumentRepo extends JpaRepository<PolicyDocument, Long> {

}

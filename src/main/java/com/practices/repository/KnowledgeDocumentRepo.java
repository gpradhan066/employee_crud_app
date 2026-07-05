package com.practices.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.practices.entity.KnowledgeDocument;

public interface KnowledgeDocumentRepo extends JpaRepository<KnowledgeDocument, Long> {

}

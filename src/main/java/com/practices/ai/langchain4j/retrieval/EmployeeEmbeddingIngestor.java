package com.practices.ai.langchain4j.retrieval;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Component
public class EmployeeEmbeddingIngestor {
    private final EmployeeRepo employeeRepo;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public EmployeeEmbeddingIngestor(
            EmployeeRepo employeeRepo, EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.employeeRepo = employeeRepo;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ingestOnStartup() {
        refresh();
    }

    public void refresh() {
        embeddingStore.removeAll();
        List<Employee> employees = employeeRepo.findAll();
        for (Employee employee : employees) {
            TextSegment segment = TextSegment.from(describe(employee),
                    Metadata.from("employeeId", String.valueOf(employee.getId())));
            embeddingStore.add(embeddingModel.embed(segment).content(), segment);
        }
    }

    private String describe(Employee employee) {
        return ("Employee %s works in the %s department, earns a salary of %s, "
                + "has a performance rating of %s and %s years of experience.").formatted(
                employee.getName(), employee.getDepartment(), employee.getSalary(),
                employee.getPerformance(), employee.getExperience());
    }
}

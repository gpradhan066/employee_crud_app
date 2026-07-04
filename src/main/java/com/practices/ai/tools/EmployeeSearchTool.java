package com.practices.ai.tools;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.practices.ai.controller.dto.SearchResponse.SearchResult;
import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;

@Component
public class EmployeeSearchTool {
    private final EmployeeRepo employeeRepo;

    public EmployeeSearchTool(EmployeeRepo employeeRepo) { this.employeeRepo = employeeRepo; }

    public List<SearchResult> search(String query) {
        String needle = query.toLowerCase(Locale.ROOT);
        return employeeRepo.findAll().stream()
                .filter(employee -> contains(employee.getName(), needle) || contains(employee.getDepartment(), needle)
                        || contains(employee.getPerformance(), needle))
                .limit(20)
                .map(this::toResult)
                .toList();
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private SearchResult toResult(Employee employee) {
        return new SearchResult("employee", employee.getName(),
                "Department: " + employee.getDepartment() + ", performance: " + employee.getPerformance());
    }
}

package com.practices.ai.mcp.resources;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practices.ai.mcp.tools.EmployeeMcpTools;
import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;

/**
 * Read-only MCP resources exposing employee data as JSON, for external MCP clients to
 * load directly into their context without a tool call.
 */
@Component
public class EmployeeMcpResources {
    private final EmployeeRepo employeeRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmployeeMcpResources(EmployeeRepo employeeRepo) {
        this.employeeRepo = employeeRepo;
    }

    @McpResource(uri = "employee://all", name = "All Employees",
            description = "JSON array of every employee record currently in the system.",
            mimeType = "application/json")
    public String allEmployees() {
        return toJson(employeeRepo.findAll());
    }

    @McpResource(uri = "employee://statistics", name = "Employee Statistics",
            description = "Aggregate JSON snapshot: total count, average/min/max salary, "
                    + "and headcount broken down by department and performance rating.",
            mimeType = "application/json")
    public String statistics() {
        List<Employee> employees = employeeRepo.findAll();
        return toJson(EmployeeMcpTools.buildStatistics(employees));
    }

    @McpResource(uri = "employee://{id}", name = "Employee By Id",
            description = "JSON detail for a single employee, looked up by id.",
            mimeType = "application/json")
    public String employeeById(String id) {
        Long employeeId;
        try {
            employeeId = Long.valueOf(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Employee id must be numeric, got '" + id + "'.");
        }
        Employee employee = employeeRepo.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException("No employee found with id " + employeeId + "."));
        return toJson(employee);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize MCP resource payload.", ex);
        }
    }
}

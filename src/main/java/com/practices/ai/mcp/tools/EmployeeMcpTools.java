package com.practices.ai.mcp.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;
import com.practices.service.EmployeeService;

/**
 * Employee CRUD/search/statistics tools exposed over MCP (Model Context Protocol) for
 * external MCP clients (Claude Desktop, Cursor, MCP Inspector, etc). Deliberately kept
 * separate from {@link com.practices.ai.tools.EmployeeCrudTools}, which exists for the
 * in-app Spring AI ChatClient function-calling feature - the two are different
 * consumers of the same underlying EmployeeService/EmployeeRepo.
 */
@Component
public class EmployeeMcpTools {
    private final EmployeeService employeeService;
    private final EmployeeRepo employeeRepo;

    public EmployeeMcpTools(EmployeeService employeeService, EmployeeRepo employeeRepo) {
        this.employeeService = employeeService;
        this.employeeRepo = employeeRepo;
    }

    @McpTool(name = "get_employee", description = "Get a single employee by id.")
    public Employee getEmployee(@McpToolParam(description = "Id of the employee to fetch") Long id) {
        return employeeRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No employee found with id " + id + "."));
    }

    @McpTool(name = "create_employee", description = "Create a new employee record and return the saved employee, including its generated id.")
    public Employee createEmployee(
            @McpToolParam(description = "Full name of the employee") String name,
            @McpToolParam(description = "Department the employee belongs to") String department,
            @McpToolParam(description = "Annual salary") Double salary,
            @McpToolParam(description = "Performance rating, e.g. good, average, poor") String performance,
            @McpToolParam(description = "Years of experience") Integer experience,
            @McpToolParam(required = false, description = "Job role or title, e.g. Java Developer") String role) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setDepartment(department);
        employee.setSalary(salary);
        employee.setPerformance(performance);
        employee.setExperience(experience);
        employee.setRole(role);
        return employeeService.saveEmployee(employee);
    }

    @McpTool(name = "update_employee", description = "Update an existing employee identified by id, replacing all of its fields, and return the updated employee.")
    public Employee updateEmployee(
            @McpToolParam(description = "Id of the employee to update") Long id,
            @McpToolParam(description = "Full name of the employee") String name,
            @McpToolParam(description = "Department the employee belongs to") String department,
            @McpToolParam(description = "Annual salary") Double salary,
            @McpToolParam(description = "Performance rating, e.g. good, average, poor") String performance,
            @McpToolParam(description = "Years of experience") Integer experience,
            @McpToolParam(required = false, description = "Job role or title, e.g. Java Developer") String role) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setDepartment(department);
        employee.setSalary(salary);
        employee.setPerformance(performance);
        employee.setExperience(experience);
        employee.setRole(role);
        return employeeService.updateEmployee(id, employee);
    }

    @McpTool(name = "delete_employee", description = "Delete an employee by id and confirm whether the deletion succeeded.")
    public DeletionResult deleteEmployee(@McpToolParam(description = "Id of the employee to delete") Long id) {
        if (!employeeRepo.existsById(id)) {
            return new DeletionResult(id, false, "No employee found with id " + id + ".");
        }
        employeeService.deleteEmployee(id);
        return new DeletionResult(id, true, "Employee " + id + " deleted successfully.");
    }

    @McpTool(name = "employee_search", description = "Search employees by name, department, role, or performance rating (case-insensitive, partial match). "
            + "Leave query blank to list every employee.")
    public List<Employee> employeeSearch(
            @McpToolParam(required = false, description = "Free-text search query; matched against name, department, role and performance") String query) {
        if (query == null || query.isBlank()) {
            return employeeRepo.findAll();
        }
        String needle = query.toLowerCase(Locale.ROOT);
        return employeeRepo.findAll().stream()
                .filter(employee -> contains(employee.getName(), needle)
                        || contains(employee.getDepartment(), needle)
                        || contains(employee.getRole(), needle)
                        || contains(employee.getPerformance(), needle))
                .toList();
    }

    @McpTool(name = "employee_statistics", description = "Aggregate employee statistics: total count, average/min/max salary, "
            + "and a breakdown of headcount per department and performance rating.")
    public StatisticsResult employeeStatistics() {
        List<Employee> employees = employeeRepo.findAll();
        return buildStatistics(employees);
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    public static StatisticsResult buildStatistics(List<Employee> employees) {
        List<Double> salaries = employees.stream()
                .map(Employee::getSalary)
                .filter(salary -> salary != null)
                .toList();
        double average = salaries.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = salaries.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = salaries.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        Map<String, Long> byDepartment = new LinkedHashMap<>();
        Map<String, Long> byPerformance = new LinkedHashMap<>();
        for (Employee employee : employees) {
            String department = employee.getDepartment() == null ? "Unspecified" : employee.getDepartment();
            String performance = employee.getPerformance() == null ? "Unspecified" : employee.getPerformance();
            byDepartment.merge(department, 1L, Long::sum);
            byPerformance.merge(performance, 1L, Long::sum);
        }

        return new StatisticsResult(employees.size(), average, min, max, byDepartment, byPerformance);
    }

    public record DeletionResult(Long id, boolean deleted, String message) {
    }

    public record StatisticsResult(int employeeCount, double averageSalary, double minSalary, double maxSalary,
            Map<String, Long> departmentBreakdown, Map<String, Long> performanceBreakdown) {
    }
}

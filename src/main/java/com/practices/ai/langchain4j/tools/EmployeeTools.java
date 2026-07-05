package com.practices.ai.langchain4j.tools;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;

import dev.langchain4j.agent.tool.Tool;

@Component
public class EmployeeTools {
    private final EmployeeRepo employeeRepo;

    public EmployeeTools(EmployeeRepo employeeRepo) {
        this.employeeRepo = employeeRepo;
    }

    @Tool("List every employee with their department, salary, performance rating and years of experience")
    public String listEmployees() {
        List<Employee> employees = employeeRepo.findAll();
        if (employees.isEmpty()) {
            return "No employees found.";
        }
        return employees.stream().map(this::describe).collect(Collectors.joining("\n"));
    }

    @Tool("Find an employee's details by name (case-insensitive, partial match allowed)")
    public String findEmployeeByName(String name) {
        List<Employee> matches = employeeRepo.findAll().stream()
                .filter(employee -> employee.getName() != null
                        && employee.getName().toLowerCase().contains(name.toLowerCase()))
                .toList();
        if (matches.isEmpty()) {
            return "No employee found matching \"" + name + "\".";
        }
        return matches.stream().map(this::describe).collect(Collectors.joining("\n"));
    }

    private String describe(Employee employee) {
        return "id=%d, name=%s, department=%s, salary=%s, performance=%s, experience=%s years".formatted(
                employee.getId(), employee.getName(), employee.getDepartment(),
                employee.getSalary(), employee.getPerformance(), employee.getExperience());
    }
}

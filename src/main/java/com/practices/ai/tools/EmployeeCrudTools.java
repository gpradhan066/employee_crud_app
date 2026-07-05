package com.practices.ai.tools;

import java.util.List;
import java.util.Locale;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.practices.entity.Employee;
import com.practices.repository.EmployeeRepo;
import com.practices.service.EmployeeService;

@Component
public class EmployeeCrudTools {
    private final EmployeeService employeeService;
    private final EmployeeRepo employeeRepo;

    public EmployeeCrudTools(EmployeeService employeeService, EmployeeRepo employeeRepo) {
        this.employeeService = employeeService;
        this.employeeRepo = employeeRepo;
    }

    @Tool(description = "Create a new employee record and return the saved employee, including its generated id.")
    public Employee createEmployee(
            @ToolParam(description = "Full name of the employee") String name,
            @ToolParam(description = "Department the employee belongs to") String department,
            @ToolParam(description = "Annual salary") Double salary,
            @ToolParam(description = "Performance rating, e.g. good, average, poor") String performance,
            @ToolParam(description = "Years of experience") Integer experience) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setDepartment(department);
        employee.setSalary(salary);
        employee.setPerformance(performance);
        employee.setExperience(experience);
        return employeeService.saveEmployee(employee);
    }

    @Tool(description = "Update an existing employee identified by id, replacing all of its fields, "
            + "and return the updated employee.")
    public Employee updateEmployee(
            @ToolParam(description = "Id of the employee to update") Long id,
            @ToolParam(description = "Full name of the employee") String name,
            @ToolParam(description = "Department the employee belongs to") String department,
            @ToolParam(description = "Annual salary") Double salary,
            @ToolParam(description = "Performance rating, e.g. good, average, poor") String performance,
            @ToolParam(description = "Years of experience") Integer experience) {
        Employee employee = new Employee();
        employee.setName(name);
        employee.setDepartment(department);
        employee.setSalary(salary);
        employee.setPerformance(performance);
        employee.setExperience(experience);
        return employeeService.updateEmployee(id, employee);
    }

    @Tool(description = "Delete an employee by id and confirm whether the deletion succeeded.")
    public DeletionResult deleteEmployee(@ToolParam(description = "Id of the employee to delete") Long id) {
        if (!employeeRepo.existsById(id)) {
            return new DeletionResult(id, false, "No employee found with id " + id + ".");
        }
        employeeService.deleteEmployee(id);
        return new DeletionResult(id, true, "Employee " + id + " deleted successfully.");
    }

    @Tool(description = "List every employee in the system, with no filtering. "
            + "Use this for general requests like 'show me the employees' or 'list all employees'.")
    public List<Employee> listAllEmployees() {
        return employeeRepo.findAll();
    }

    @Tool(description = "Find employees by name (case-insensitive, partial match) and return all matches.")
    public List<Employee> findEmployee(@ToolParam(description = "Full or partial employee name") String name) {
        String needle = name.toLowerCase(Locale.ROOT);
        return employeeRepo.findAll().stream()
                .filter(employee -> contains(employee.getName(), needle))
                .toList();
    }

    @Tool(description = "Find every employee that belongs to the given department (case-insensitive, partial match).")
    public List<Employee> findEmployeesByDepartment(@ToolParam(description = "Department name") String department) {
        String needle = department.toLowerCase(Locale.ROOT);
        return employeeRepo.findAll().stream()
                .filter(employee -> contains(employee.getDepartment(), needle))
                .toList();
    }

    @Tool(description = "Find employees earning at or above a salary threshold. "
            + "If no threshold is given, the current average salary across all employees is used.")
    public List<Employee> findHighSalaryEmployees(
            @ToolParam(required = false,
                    description = "Minimum salary threshold; defaults to the current average salary") Double minSalary) {
        List<Employee> employees = employeeRepo.findAll();
        double threshold = minSalary != null ? minSalary : computeAverageSalary(employees);
        return employees.stream()
                .filter(employee -> employee.getSalary() != null && employee.getSalary() >= threshold)
                .toList();
    }

    @Tool(description = "Calculate the average salary across all employees.")
    public AverageSalaryResult averageSalary() {
        List<Employee> employees = employeeRepo.findAll();
        return new AverageSalaryResult(computeAverageSalary(employees), employees.size());
    }

    @Tool(description = "Count the total number of employees.")
    public EmployeeCountResult employeeCount() {
        return new EmployeeCountResult(employeeRepo.count());
    }

    private boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private double computeAverageSalary(List<Employee> employees) {
        return employees.stream()
                .filter(employee -> employee.getSalary() != null)
                .mapToDouble(Employee::getSalary)
                .average()
                .orElse(0.0);
    }

    public record DeletionResult(Long id, boolean deleted, String message) {
    }

    public record AverageSalaryResult(double averageSalary, int employeeCount) {
    }

    public record EmployeeCountResult(long count) {
    }
}

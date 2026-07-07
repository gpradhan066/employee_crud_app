package com.practices.ai.mcp.prompts;

import java.util.List;

import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Component;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * Reusable MCP prompt templates that guide an external AI client on how to use the
 * employee tools/resources exposed by {@link com.practices.ai.mcp.tools.EmployeeMcpTools}
 * and {@link com.practices.ai.mcp.resources.EmployeeMcpResources}.
 */
@Component
public class EmployeeMcpPrompts {

    @McpPrompt(name = "employee_onboarding",
            description = "Draft an onboarding checklist for a new employee joining a department in a given role.")
    public McpSchema.GetPromptResult onboarding(
            @McpArg(name = "name", description = "Full name of the new employee", required = true) String name,
            @McpArg(name = "department", description = "Department the employee is joining", required = true) String department,
            @McpArg(name = "role", description = "Job role or title, e.g. Java Developer", required = false) String role) {
        String roleText = (role == null || role.isBlank()) ? "" : " as a " + role;
        String text = "Create a first-week onboarding checklist for " + name + ", joining the " + department
                + " department" + roleText + ". Use the create_employee tool to add them once their salary, "
                + "performance rating and years of experience are known, then confirm the record with get_employee.";
        return userPrompt(text);
    }

    @McpPrompt(name = "salary_review",
            description = "Review salaries for a department against the company-wide average and flag outliers.")
    public McpSchema.GetPromptResult salaryReview(
            @McpArg(name = "department", description = "Department to review", required = true) String department) {
        String text = "Use the employee_search tool to list every employee in the " + department
                + " department, and the employee_statistics tool to get the company-wide average salary. "
                + "Compare each employee's salary against the company average and call out anyone paid "
                + "notably above or below it, with a brief justification.";
        return userPrompt(text);
    }

    @McpPrompt(name = "employee_search_assistant",
            description = "Guide the assistant to search employee records for a natural-language query using the employee_search tool.")
    public McpSchema.GetPromptResult searchAssistant(
            @McpArg(name = "query", description = "What to look for, e.g. a name, department, role or performance rating", required = true) String query) {
        String text = "Use the employee_search tool with query \"" + query
                + "\" to find matching employees, then summarize the results in plain language.";
        return userPrompt(text);
    }

    private McpSchema.GetPromptResult userPrompt(String text) {
        McpSchema.PromptMessage message = new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text));
        return new McpSchema.GetPromptResult(null, List.of(message));
    }
}

# AI Function Calling (Spring AI Tools) Guide

## 1. Overview

This document explains the Spring AI **function calling** (tool calling) feature added on top
of the existing Spring AI integration (see `doc/spring_ai.md`). It lets the chat model
invoke real Employee CRUD operations - create, update, delete, and several read/aggregate
queries - instead of answering from guesswork.

The implementation adds exactly one new class,
`src/main/java/com/practices/ai/tools/EmployeeCrudTools.java`, wires it into the existing
`AIService`, and extends the existing system prompt. **No existing endpoint, controller, or
CRUD behavior was changed.**

| Stack | Endpoint | Tool calling |
|---|---|---|
| Spring AI (this document) | `/api/ai/chat`, `/api/ai/chat/stream` | Yes - `EmployeeCrudTools` |
| LangChain4j (`doc/langchain4j.md`) | `/api/ai/langchain/*` | Yes - separate `EmployeeTools` class (read-only: list/find by name) |

The two tool sets are intentionally independent implementations in independent packages, one
per AI stack - see `doc/langchain4j.md` §2 for why two stacks exist at all.

## 2. What "function calling" means here

Normally a chat model can only respond using what's in the prompt. Function/tool calling lets
the model additionally see a list of callable operations (name, description, parameters) and,
mid-response, request that one be invoked with specific arguments. Spring AI intercepts that
request, calls the real Java method, feeds the return value back to the model as a new turn,
and only then does the model produce its final natural-language answer.

This is why a tool-using request costs **two** model round-trips (decide-to-call, then
final-answer) instead of one - measured directly during implementation and documented in §9.

## 3. High-level architecture

```mermaid
flowchart LR
    UI[Angular AI Assistant] -->|POST /api/ai/chat| AC[AiController]
    AC --> ACS[AiChatService]
    ACS --> AIS[AIService]

    AIS --> PT[PromptTemplate: employee-assistant-system.st]
    AIS --> CC[Spring AI ChatClient]
    AIS -->|.tools(employeeCrudTools)| TOOLS[EmployeeCrudTools]

    TOOLS --> ES[EmployeeService]
    TOOLS --> REPO[EmployeeRepo]
    ES --> REPO
    REPO --> DB[(PostgreSQL emp_db)]

    CC -->|HTTP localhost:11434| OLLAMA[Ollama qwen3:1.7b]
```

## 4. The tools

File:

```text
src/main/java/com/practices/ai/tools/EmployeeCrudTools.java
```

The task asked for 8 functions. A 9th, `listAllEmployees`, was added after testing surfaced a
real gap - see §8.2.

| Tool | Parameters | Backed by | Returns |
|---|---|---|---|
| `createEmployee` | name, department, salary, performance, experience (all required) | `EmployeeService.saveEmployee` | `Employee` |
| `updateEmployee` | id, name, department, salary, performance, experience (all required) | `EmployeeService.updateEmployee` | `Employee` |
| `deleteEmployee` | id | `EmployeeService.deleteEmployee` | `DeletionResult` |
| `listAllEmployees` | none | `EmployeeRepo.findAll()` | `List<Employee>` |
| `findEmployee` | name (partial, case-insensitive) | `EmployeeRepo.findAll()` + filter | `List<Employee>` |
| `findEmployeesByDepartment` | department (partial, case-insensitive) | `EmployeeRepo.findAll()` + filter | `List<Employee>` |
| `findHighSalaryEmployees` | minSalary (optional - defaults to current average) | `EmployeeRepo.findAll()` + filter | `List<Employee>` |
| `averageSalary` | none | `EmployeeRepo.findAll()` + stream average | `AverageSalaryResult` |
| `employeeCount` | none | `EmployeeRepo.count()` | `EmployeeCountResult` |

Each method is annotated `@Tool(description = "...")` from
`org.springframework.ai.tool.annotation.Tool`, and each parameter is annotated
`@ToolParam(description = "...")` from `org.springframework.ai.tool.annotation.ToolParam`.
The description text is what the model actually reads when deciding whether and how to call a
tool - it is not documentation, it is part of the prompt.

Example (`employeeCount`, the simplest case):

```java
@Tool(description = "Count the total number of employees.")
public EmployeeCountResult employeeCount() {
    return new EmployeeCountResult(employeeRepo.count());
}
```

Example (`findHighSalaryEmployees`, showing an optional parameter):

```java
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
```

### 4.1 Reused, not duplicated, business logic

`createEmployee`, `updateEmployee`, and `deleteEmployee` call the **existing**
`com.practices.service.EmployeeService` methods - the same ones the plain REST controller
(`EmpController`) uses. This was a deliberate choice to satisfy "do not break any existing
logic": the tool layer adds a new way to *invoke* the same business logic, it does not
reimplement it. In particular, `updateEmployee` inherits the existing full-replace semantics
(every field is overwritten, there is no partial-patch mode) - see §8.3 for the prompt-level
consequence of this.

Read-only tools (`listAllEmployees`, `findEmployee`, `findEmployeesByDepartment`,
`findHighSalaryEmployees`, `averageSalary`, `employeeCount`) go straight to `EmployeeRepo`,
matching the existing convention already used by `EmployeeSearchTool` (the tool backing the
unrelated `/api/ai/search` endpoint).

## 5. Wiring: `AIService`

File:

```text
src/main/java/com/practices/ai/service/AIService.java
```

`AIService` already built every chat request through `ChatClient.prompt()...options(...)`.
The only structural change was adding one line and one constructor dependency:

```java
public AIService(
        ChatClient chatClient,
        @Value("classpath:/prompts/employee-assistant-system.st") Resource systemPrompt,
        @Value("classpath:/prompts/employee-assistant-user.st") Resource userPrompt,
        EmployeeCrudTools employeeCrudTools) {   // <-- new
    ...
}

return chatClient.prompt()
        .system(renderedSystemPrompt)
        .user(renderedUserPrompt)
        .tools(employeeCrudTools)   // <-- new: exposes every @Tool method on this bean
        .options(options);
```

`ChatClient.ChatClientRequestSpec.tools(Object...)` reflects over the given object(s) for
`@Tool`-annotated methods and attaches their specifications to the request. No
`ToolCallbackProvider` or manual registration was needed for this single-object case.

This applies to **every** call through `/api/ai/chat` and `/api/ai/chat/stream` - there is no
separate "tools on/off" flag. A message that doesn't need a tool (e.g. "how do I apply for
leave?") simply doesn't trigger one; the model decides per-request based on the tool
descriptions and the user's message.

## 6. Structured JSON output

The task required tool results to come back as structured JSON, not free text. This is
satisfied by return type alone - no manual JSON construction exists anywhere in this file.
Spring AI's tool-calling machinery serializes whatever a `@Tool` method returns (via Jackson)
before sending it back to the model as the tool's result:

```java
public record DeletionResult(Long id, boolean deleted, String message) { }
public record AverageSalaryResult(double averageSalary, int employeeCount) { }
public record EmployeeCountResult(long count) { }
```

`createEmployee`/`updateEmployee` return the existing `Employee` JPA entity directly - the
same plain-field, no-lazy-association entity already returned as JSON by `EmpController`
today, so this introduces no new serialization behavior.

The model receives this structured JSON as the tool result, then composes its own
natural-language sentence from it for the user - the *tool* output is structured; the
*user-facing chat reply* is deliberately plain text (see §8.4).

## 7. Prompt changes

File:

```text
src/main/resources/prompts/employee-assistant-system.st
```

Adding tools alone is not sufficient - a small model like `qwen3:1.7b` needs to be told when
and how to use them, or it reverts to guessing (see §8 for what happens if you skip this).
The final prompt has five additions beyond the original persona/formatting rules from
`doc/spring_ai.md`:

```text
This chat interface displays plain text only and does not render markdown. Never use markdown syntax
such as **bold**, _italic_, `code`, or # headings. For lists, write each item on its own line starting
with a plain number or dash, for example "1. Name - Department - Salary", with no other symbols around
words.

You have tools to create, update, delete, and look up employee records - including salary, department,
headcount, and average-salary queries. Always call the appropriate tool to get real data before answering
a factual question about a specific employee or number. Never guess, estimate, or invent employee data -
if a tool returns no result, say so plainly.

Before calling createEmployee, you must already have the employee's name, department, salary, performance
rating, and years of experience from the user's own words in this conversation. If any of these are
missing, ask the user for exactly the missing details instead of calling the tool or inventing placeholder
values such as "John Doe" or example numbers.

updateEmployee replaces every field on the employee, so before calling it for a partial change (for
example, "give Jane a raise"), first call findEmployee or findEmployeesByDepartment to get the employee's
current id, department, performance, and experience, then call updateEmployee with the new value plus
those unchanged current values.

To list every employee, call listAllEmployees. Only use findEmployeesByDepartment when the user names a
specific department.
```

Plus a third few-shot example demonstrating the "ask before creating" behavior:

```text
Example 3:
User: I want to save a new employee.
Assistant: Sure - what's the employee's name, department, salary, performance rating, and years of experience?
```

The pre-existing blanket rule "Never expose confidential information" and its matching
example ("I cannot share personal compensation information...") were **removed**, because
they directly contradicted the new tools - salary is now a legitimately queryable field via
`findEmployee`, `findHighSalaryEmployees`, and `averageSalary`, and the old wording caused the
model to refuse those questions outright.

## 8. Bugs found during implementation and how they were fixed

These were caught by actually exercising the feature end-to-end against the real local model,
not assumed away. Documented here because they are the actual reason the prompt looks the way
it does - future prompt edits should not casually remove these rules without re-testing the
scenario that motivated them.

### 8.1 The model invented a fake employee instead of asking for details

**Symptom:** "i want to save new employee" (no details given) immediately produced a
`createEmployee` call with placeholder data ("John Doe", Engineering, 85000, Excellent, 5
years) - a real row was written to the database.

**Cause:** the original prompt said "use tools instead of guessing answers" but never said
anything about *withholding* a mutating tool call until required information is actually
present. The model satisfied "use the tool" by inventing values for the required parameters.

**Fix:** the "Before calling createEmployee..." paragraph in §7, plus Example 3. Re-tested:
the same input now produces "Please provide the employee's name, department, salary,
performance rating, and years of experience." with no tool call and no database write.

### 8.2 "Show me list of employee" returned an empty result

**Symptom:** a generic list request returned "There are no employees in the 'All'
department."

**Cause:** none of the original 8 tools could answer "list everyone" - the closest match was
`findEmployeesByDepartment`, so the model called it with a literal, non-existent department
value of `"All"`.

**Fix:** added the `listAllEmployees()` tool (§4) and the instruction "To list every employee,
call listAllEmployees. Only use findEmployeesByDepartment when the user names a specific
department." This is a genuine gap in the originally-specified 8 functions, not a prompting
issue alone - a 9th tool was required.

### 8.3 The model echoed a literal `"Assistant:"` prefix

**Symptom:** a correct clarifying-question response came back as
`"Assistant: Please provide the employee's..."` - the label itself leaked into the visible
chat message.

**Cause:** the few-shot examples in the prompt are written as `User: ...` / `Assistant: ...`
dialogue turns for readability. The model sometimes pattern-matches that formatting and
reproduces the `Assistant:` label literally in its own output.

**Fix:** two layers, since this is the third distinct prompt-echo bug found this session
(the others being a `Summary:/Action:/Notes:` scaffold, and a meta-description like
`"(calls the findEmployee tool...)"` - both from earlier work, see `doc/spring_ai.md`
history):

1. Prompt: explicitly forbid an `"Assistant:"` prefix (§7, formatting rule).
2. Code: a defensive strip in `AIService.chat()`, independent of prompt compliance:

```java
private String stripAssistantPrefix(String answer) {
    if (answer == null) {
        return answer;
    }
    String trimmed = answer.strip();
    if (trimmed.regionMatches(true, 0, "assistant:", 0, "assistant:".length())) {
        return trimmed.substring("assistant:".length()).strip();
    }
    return trimmed;
}
```

This only strips a literal leading label - it does not otherwise alter the model's answer.

### 8.4 Markdown syntax leaked into a plain-text chat bubble

**Symptom:** a list response used `**Name**` bold markers; the Angular chat window renders
message text with plain interpolation (`{{ message.content }}`), not an HTML/markdown
renderer, so the user saw literal asterisks instead of bold text.

**Fix:** the "This chat interface displays plain text only..." paragraph in §7, explicitly
banning `**bold**`, `_italic_`, `` `code` ``, and `#` headings, with a concrete plain-list
format example.

**General takeaway:** with a small local model, defensive prompting has to be concrete and
example-driven (few-shot beats abstract instruction), and the highest-risk failure mode is the
model imitating the *shape* of the prompt's own formatting/examples rather than following the
instruction behind them. Any future prompt edit that adds a new example should assume the
model may echo it verbatim, and should be reviewed with that in mind.

## 9. Verified behavior

Each item below was checked against the real database before and after the chat call, not
just trusted from the model's claimed answer:

| Test | Result |
|---|---|
| `employeeCount` | Correctly reported 5 (matched direct REST count) |
| `createEmployee` (full details given) | Row genuinely inserted; verified via `GET /employee/getall` |
| `findEmployee` + `averageSalary` + `findHighSalaryEmployees` (combined in one message) | Average computed exactly right (31224.17 = 187345 / 6); high-salary filter correctly excluded the one low earner |
| `updateEmployee` | Salary genuinely changed in the database (12345.0 -> 99999.0) |
| `deleteEmployee` | Row genuinely removed; count returned to the original 5 |
| `findEmployeesByDepartment` ("Engg") | Correct 3-employee list, matching real data |
| `listAllEmployees` ("show me list of employee") | Returned all real employees after the fix in §8.2 |
| Plain non-employee question ("how do I apply for leave?") | Answered directly in ~9 seconds, no tool call - confirms tools don't add overhead to unrelated chat |
| Existing `/employee/*`, `/api/ai/health`, `/api/ai/models` | Rechecked working, unaffected |

Tool-calling requests measured 30 seconds to a little over 2 minutes end-to-end on the
development machine (Intel i3, Windows 10) - see §10.

## 10. Performance note

A tool-calling turn costs two sequential calls to the local model (decide-to-call-tool, then
compose-final-answer from the tool result), on top of whatever "thinking" overhead is active
for that request. On constrained hardware this is the dominant cost, not the tool execution
itself (a `findAll()` query is effectively instant).

This project already has:

- A per-request "Thinking mode" toggle (`think` field on `ChatRequest`) that disables Ollama's
  `qwen3` reasoning trace - see `doc/spring_ai.md` and the Angular AI panel. Turning it off is
  the single biggest lever on latency, tool-calling or not.
- `app.ai.providers.ollama.timeout=240s` to accommodate slow tool-calling turns on this
  hardware.

## 11. Security considerations

In addition to the general Spring AI security notes (`doc/spring_ai.md` §24):

- **The model can now write and delete data.** `createEmployee`, `updateEmployee`, and
  `deleteEmployee` are reachable from a chat message with no additional confirmation step. On
  a production deployment, add an authorization check and/or a human-confirmation step before
  a mutating tool actually commits, rather than trusting prompt instructions alone to prevent
  unwanted writes.
- The salary-refusal rule was intentionally removed (§7) so the tools work as requested - if
  salary should be restricted to certain callers, enforce that in
  `EmployeeCrudTools`/`EmployeeService` (a real access-control check), not only in the prompt.
- `deleteEmployee` is irreversible and requires only a numeric id - a model mistake (wrong id)
  or a prompt-injection attempt embedded in a chat message could delete the wrong record. No
  soft-delete or confirmation step exists today.

## 12. File reference

| File | Responsibility |
|---|---|
| `ai/tools/EmployeeCrudTools.java` | All 9 `@Tool` methods (8 requested + `listAllEmployees`) |
| `ai/service/AIService.java` | Wires `.tools(employeeCrudTools)` into every chat request; strips a leaked `"Assistant:"` prefix |
| `prompts/employee-assistant-system.st` | Tool-usage rules, ask-before-creating rule, plain-text/no-markdown rule, few-shot examples |
| `service/EmployeeService.java` | Existing business logic reused by `createEmployee`/`updateEmployee`/`deleteEmployee` (unchanged) |
| `repository/EmployeeRepo.java` | Existing repository reused by all read/aggregate tools (unchanged) |

No other file was modified to implement this feature - `EmpController`, `EmployeeService`,
`EmployeeRepo`, and the Angular frontend are all unchanged.

## 13. Architectural rules

1. Tool methods call existing services/repositories - they never duplicate business logic or
   bypass validation that the REST controllers rely on.
2. `updateEmployee`'s tool signature matches the existing full-replace contract exactly; if
   that contract ever changes, update both the REST controller and this tool together.
3. New tools are added to `EmployeeCrudTools` only when a real, demonstrated gap exists (see
   §8.2) - not speculatively.
4. Prompt rules that exist because of an observed bug (§8) must not be removed without
   re-testing the exact scenario that motivated them.
5. Tool result types are structured Java objects (records or existing entities), never
   hand-built JSON strings.
6. This feature must not change `/api/ai/chat`'s request or response JSON shape - `think` and
   `model` continue to work exactly as before; tools are purely additive server-side behavior.

# Employee AI Agent Guide

## 1. Overview

This document explains the **Employee AI Agent** - an explicit Reason -> Plan -> Execute ->
Summarize orchestrator for bulk and single employee actions, built on top of the existing
Spring AI integration (`doc/spring_ai.md`, `doc/function_calling.md`).

It is a fourth, independent AI entry point alongside the three already documented:

| Stack | Endpoint(s) | Model's role |
|---|---|---|
| Spring AI chat | `/api/ai/chat` | Answers directly; may call one `@Tool` per turn |
| Spring AI function calling | same as above, via `EmployeeCrudTools` | Decides **and** executes a single tool call |
| LangChain4j | `/api/ai/langchain/*` | Chat-oriented AI Service with its own tools/memory/RAG |
| **Employee AI Agent (this doc)** | `/api/ai/agent/plan`, `/api/ai/agent/confirm` | Decides **only** - interprets intent into a structured plan; never touches the database itself |

The agent exists to safely handle instructions like *"Give every Java developer under Engg a
15% salary hike"* - a bulk, multi-record, irreversible write - which is a fundamentally
different risk profile from "what is Jon's salary?". See §9 for why this needed a distinct
design rather than just more function-calling tools.

## 2. Design decisions

Two decisions were made explicitly before writing any code, because they affect data safety
and the data model:

1. **The "Java developer" filter needs a real field.** The `Employee` entity had no
   role/title field. A nullable `role` column was added (§3) rather than trying to fake
   role-matching against `name`/`department`/`performance`.
2. **Plan first, execute only on confirmation.** A bulk salary write across an unknown number
   of rows, based on a natural-language instruction, is not something to fire automatically.
   The agent always proposes a plan and requires an explicit second call to actually write
   anything - see §6.

## 3. Data model change

File:

```text
src/main/java/com/practices/entity/Employee.java
```

```java
private String name;
private String department;
private Double salary;
private String performance;
private Integer experience;
private String role;   // added
```

Additive and nullable - `spring.jpa.hibernate.ddl-auto=update` added the column
automatically on next startup; every existing row got `role = null`. No existing REST
endpoint, DTO, or Angular component was changed because of this - `EmpController`,
`EmployeeService`, and the employee list/form UI are untouched (the new field simply appears
as `"role": null` in existing JSON responses).

**A real bug this surfaced:** `EmployeeService.updateEmployee` copies fields one-by-one onto
the existing entity rather than saving the incoming object directly:

```java
existingEmployee.setName(emp.getName());
existingEmployee.setDepartment(emp.getDepartment());
existingEmployee.setSalary(emp.getSalary());
existingEmployee.setPerformance(emp.getPerformance());
existingEmployee.setExperience(emp.getExperience());
existingEmployee.setRole(emp.getRole());   // added - without this line, role updates silently no-op
```

Without the added line, any attempt to set `role` through the existing update path (REST PUT,
the `EmployeeCrudTools` tool, or the agent's own execute step, all of which ultimately call
this method) would appear to succeed but never actually persist the new role. This was fixed
before any agent testing began.

`EmployeeCrudTools.createEmployee`/`updateEmployee` (see `doc/function_calling.md`) were also
extended with an optional `role` parameter, so role can be set through ordinary chat, not only
through direct API calls.

## 4. High-level architecture

```mermaid
flowchart LR
    UI[Angular AI Assistant - Agent mode] -->|POST /api/ai/agent/plan| PC[EmployeeAgentController]
    UI -->|POST /api/ai/agent/confirm| PC

    PC --> AS[EmployeeAgentService]

    AS -->|Reason| PT[PromptTemplate: employee-agent-plan.st]
    AS -->|Reason| CC[Spring AI ChatClient .entity(AgentPlan.class)]
    CC -->|HTTP localhost:11434| OLLAMA[Ollama qwen3:1.7b]

    AS -->|Plan: match + calculate, no writes| REPO[EmployeeRepo]
    AS -->|holds proposed plan in memory| PENDING[(pendingPlans map)]

    AS -->|Execute: only after confirm| ES[EmployeeService.updateEmployee]
    ES --> REPO
    REPO --> DB[(PostgreSQL emp_db)]

    AS -->|Summarize: built from real applied changes| MEM[ConversationMemory]
```

## 5. Package structure

```text
com.practices.ai.agent
├── EmployeeAgentService.java           The Reason/Plan/Execute/Summarize orchestrator
├── dto/
│   ├── AgentPlan.java                  Structured LLM output (Reason stage)
│   ├── ProposedChange.java             One employee's before/after value
│   ├── AgentPlanRequest.java           POST /plan request body
│   ├── AgentPlanResponse.java          POST /plan response body
│   ├── AgentConfirmRequest.java        POST /confirm request body
│   └── AgentExecutionResponse.java     POST /confirm response body
└── controller/
    ├── EmployeeAgentController.java        /api/ai/agent/plan and /confirm
    └── EmployeeAgentExceptionHandler.java   Scoped exception handling
```

Prompt template:

```text
src/main/resources/prompts/employee-agent-plan.st
```

## 6. The four stages

### 6.1 Reason

The model's *only* job is to turn free text into a structured `AgentPlan`:

```java
public record AgentPlan(String reasoning, Filter filter, Action action, String clarificationNeeded) {
    public record Filter(String department, String role, String performance,
            Double minSalary, Double maxSalary, String nameContains) { }
    public record Action(String type, Double amount, String textValue) { }
}
```

- `filter` - which employees this applies to. Every field is optional; all-null means
  "everyone". `action.type` is one of `increase_salary_percent`, `increase_salary_flat`,
  `set_salary`, `set_performance`, `set_role`, with the numeric value in `amount` or the new
  text in `textValue`.
- `clarificationNeeded` - if the model can't confidently fill in `filter`/`action`, it puts a
  question here instead, and the agent stops without touching the database (§6.2).

This is requested via Spring AI's native structured-output support:

```java
AgentPlan agentPlan = chatClient.prompt()
        .user(rendered)
        .options(OllamaChatOptions.builder().model(aiProperties.defaultModel()).enableThinking())
        .call()
        .entity(AgentPlan.class);
```

`.entity(AgentPlan.class)` - not `.content()` - is what makes this structured: Spring AI
generates the JSON-shape instruction and parses the response straight into the record, the
same mechanism used for `EmployeeAIAgent.analyze(...)` on the LangChain4j side
(`doc/langchain4j.md` §7, §11), just via Spring AI's own converter instead of LangChain4j's.

Thinking is left **enabled** for this call specifically (unlike the regular chat path, which
defaults it off) - deliberately trading latency for reliability, since misreading "15%" as a
flat amount on a bulk payroll write is a worse outcome than a slower response. See §10.

The prompt template (`employee-agent-plan.st`) explicitly tells the model:

> Never invent employee names, ids, or numbers - you are only producing a filter and an
> action, not employee data. The actual matching and calculation happens after you respond,
> against the real database.

### 6.2 Plan

Everything past this point is **deterministic Java, not the model**. Given the `AgentPlan`:

```java
List<Employee> matches = employeeRepo.findAll().stream()
        .filter(employee -> matchesFilter(employee, agentPlan.filter()))
        .toList();

List<ProposedChange> changes = matches.stream()
        .map(employee -> proposeChange(employee, agentPlan.action()))
        .filter(Objects::nonNull)
        .toList();
```

`matchesFilter` applies each non-null filter field as a case-insensitive `contains` (or a
numeric range for salary) against the real `Employee` rows - AND-combined, so "Java developers
under Engg" requires both `role` and `department` to match the same row.
`proposeChange` computes the after-value for the requested action **without mutating
anything** - it's used both for the preview (here) and for logging what actually changed
during execute (§6.3), so the two can never disagree.

Nothing is written yet. Instead, the match is stashed:

```java
String planId = UUID.randomUUID().toString();
pendingPlans.put(planId, new PendingPlan(
        conversationId, agentPlan.action(), matches.stream().map(Employee::getId).toList(), now));
```

`pendingPlans` is an in-memory `ConcurrentHashMap<String, PendingPlan>`, swept of entries older
than 15 minutes (`PLAN_TTL`) on every `plan()` call - the same "in-memory, non-persistent"
tradeoff already accepted elsewhere in this codebase (`InMemoryConversationMemory`,
LangChain4j's `InMemoryEmbeddingStore` - see `doc/spring_ai.md` §15 and `doc/langchain4j.md`
§15). A plan does not survive a backend restart, and only re-storing the employee **ids**
(not a snapshot of their data) means confirm always re-reads current values at execute time.

The response returned to the caller (`AgentPlanResponse`) includes the full `proposedChanges`
list and a `planId` - nothing else is needed to render a preview or to confirm/cancel it.

### 6.3 Execute

Only `POST /api/ai/agent/confirm` with `confirm: true` reaches this code, and only for a
`planId` that actually exists and belongs to the given `conversationId`:

```java
for (Long employeeId : pending.employeeIds()) {
    employeeRepo.findById(employeeId).ifPresent(employee -> {
        ProposedChange change = proposeChange(employee, pending.action());
        if (change == null) return;
        applyAction(employee, pending.action());
        employeeService.updateEmployee(employeeId, employee);
        applied.add(change);
        log.info("[AGENT][EXECUTE] conversationId={} employeeId={} field={} before={} after={}", ...);
    });
}
```

Each employee is re-fetched fresh (not reused from the plan step) and written through the
**existing** `EmployeeService.updateEmployee` - the same method the REST `PUT` endpoint and
`EmployeeCrudTools.updateEmployee` use. No new data-access path was introduced for writes.

`confirm: false` takes a separate, earlier branch that removes the pending plan and writes
nothing:

```java
if (!confirm) {
    log.info("[AGENT][EXECUTE] conversationId={} planId={} cancelled by caller", conversationId, planId);
    String message = "Cancelled - no changes were made.";
    ...
    return new AgentExecutionResponse(conversationId, planId, false, message, List.of(), now);
}
```

### 6.4 Summarize

The final report is built from `applied` - the changes that were *actually* written - not
generated by the model:

```java
private String renderExecutionSummary(List<ProposedChange> applied) {
    if (applied.isEmpty()) return "No changes were applied.";
    StringBuilder sb = new StringBuilder("Updated ").append(applied.size())...
    for (ProposedChange change : applied) {
        sb.append(...).append(change.name()).append(" - ").append(change.field())
          .append(" ").append(change.beforeValue()).append(" to ").append(change.afterValue());
    }
    return sb.toString();
}
```

This guarantees the summary can never claim a change that didn't happen - there is no LLM call
in the Summarize stage at all, which also makes `confirm` fast (measured well under a second,
versus 30 seconds to several minutes for `plan`'s Reason stage - see §10).

## 7. Logging reasoning steps

Every stage logs through SLF4J with a consistent `[AGENT][STAGE]` prefix, tagged with
`conversationId` (and `planId` once one exists):

```text
[AGENT][REASON]    conversationId=... instruction="..."
[AGENT][PLAN]      conversationId=... reasoning="..." filter=... action=... clarification=...
[AGENT][PLAN]      conversationId=... planId=... matchedCount=...
[AGENT][EXECUTE]   conversationId=... employeeId=... field=... before=... after=...
[AGENT][EXECUTE]   conversationId=... planId=... cancelled by caller   (cancel path)
[AGENT][SUMMARIZE] conversationId=... planId=... appliedCount=...
```

This gives a searchable audit trail per conversation/plan without needing to inspect response
bodies - important for a feature that writes payroll-adjacent data.

## 8. Conversation memory

The agent reuses the **existing** `ConversationMemory` abstraction (`doc/spring_ai.md` §15) -
no new memory system was introduced. Both the user's instruction and the agent's
plan-preview/clarification/summary text are appended as `ChatMessage`s, and prior turns are
rendered back into the Reason-stage prompt via `{history}`, the same pattern `AIService`
already uses. This means a follow-up like "actually make it 20% instead" has the prior
instruction available as context, even though it doesn't automatically re-open the previous
plan (each `plan()` call produces a fresh, independent `planId`).

## 9. Why this couldn't just be more function-calling tools

`EmployeeCrudTools` (`doc/function_calling.md`) already lets the model call `updateEmployee`
whenever it wants. Handing it a bulk instruction and trusting it to enumerate every matching
employee and call the tool correctly for each one, with no preview, is a different risk
profile than a single lookup - and this session had already demonstrated exactly how a small
local model behaves when given too much autonomy (inventing a fake employee, echoing prompt
text verbatim, guessing a nonexistent department - see `doc/function_calling.md` §8).

The agent narrows the model's job to the one thing language models are actually reliable at -
turning natural language into a structured, bounded intent - and keeps matching, arithmetic,
and the database write as plain, testable Java. See the "why this matters in industry" framing
below.

**Industry pattern.** This is the "propose -> review -> apply" shape used anywhere AI meets
bulk or irreversible change: `terraform plan` vs `apply`, an AI coding assistant proposing a
diff before it's written to disk, RPA/workflow tools with a human-in-the-loop gate, payroll
and HR systems requiring an approval step before a bulk write. The rule of thumb: autonomous
tool-calling for low-risk, single-item, reversible actions; a gated plan-then-confirm agent
for bulk, irreversible, or financially/legally sensitive ones. Salary changes are squarely in
the second bucket.

## 10. Performance and reliability notes

- **Reason is the only slow, only unreliable stage.** Plan/Execute/Summarize are plain Java
  and effectively instant; all latency and all "did the model get it right" risk is
  concentrated in one structured-output call. On the development machine (Intel i3, Windows
  10), that call measured 30 seconds to a little over 4 minutes depending on load.
- **Thinking is enabled for Reason** (`enableThinking()`), trading latency for interpretation
  reliability on a bulk/irreversible instruction - the opposite default from the regular chat
  path (`doc/spring_ai.md`), which defaults thinking off for speed. This was a deliberate,
  scenario-specific choice, not an oversight.
- **A real bug found and fixed during implementation:** building `OllamaChatOptions` without
  an explicit `.model(...)` silently falls back to Spring AI's SDK-level hardcoded default
  model name `"mistral"` - which isn't pulled on this machine - instead of inheriting the
  configured `qwen3:1.7b`. `AIService` never hit this because it always sets `.model(model)`
  explicitly; this agent was the first code path that built options without doing so. Fixed by
  injecting `AiProperties` and calling
  `OllamaChatOptions.builder().model(aiProperties.defaultModel())...`. Anyone adding a new
  Spring AI call site should set the model explicitly rather than relying on defaults.
- **A real bean-naming collision found and fixed:** the class was originally named
  `EmployeeAIAgent`, colliding with the LangChain4j class of the same simple name
  (`com.practices.ai.langchain4j.agent.EmployeeAIAgent`) - Spring's default bean naming is by
  simple class name, not fully-qualified name, so both `@Bean`/`@Service` definitions produced
  a bean named `employeeAIAgent` and the application failed to start
  (`ConflictingBeanDefinitionException`-style failure). Renamed to `EmployeeAgentService` to
  resolve it without touching the already-documented LangChain4j code.

## 11. Frontend integration

The Angular AI panel (`front_end/angular/employee-ui/src/app/ai/components/ai-panel`) gained a
third provider button, **Agent**, alongside Spring AI and LangChain4j:

```html
<button [class.active]="provider() === 'agent'" (click)="setProvider('agent')">Agent</button>
```

In Agent mode, the model dropdown and "Thinking mode" toggle (both Spring-AI-chat-specific)
are replaced with a hint: *"Describe an action ... I'll propose a plan before changing
anything."* Sending a message calls `POST /api/ai/agent/plan` instead of `/api/ai/chat`.

If the response is actionable, the assistant message renders a **plan card** instead of plain
text:

```html
@if (message.plan) {
  <div class="agent-plan-card">
    <p class="agent-plan-title">Proposed changes</p>
    <ul class="agent-plan-list">
      @for (change of message.plan.proposedChanges; track change.employeeId) {
        <li>{{ change.name }} - {{ change.field }}: {{ change.beforeValue }} to {{ change.afterValue }}</li>
      }
    </ul>
    @if (!isPlanResolved(message.plan.planId)) {
      <div class="agent-plan-actions">
        <button (click)="respondToPlan(message.plan, false)">Cancel</button>
        <button (click)="respondToPlan(message.plan, true)">Confirm</button>
      </div>
    } @else {
      <p class="agent-plan-resolved">Resolved - see the message below.</p>
    }
  </div>
}
```

`respondToPlan(plan, confirm)` calls `POST /api/ai/agent/confirm`, appends the resulting
summary as a new assistant message, and marks the plan's `planId` as resolved (tracked in a
`resolvedPlanIds` signal) so the Confirm/Cancel buttons can't be clicked twice for the same
plan. Switching providers or clearing the chat resets this set along with the conversation id.

No existing component (`EmployeeList`, `EmployeeService`, the Spring AI/LangChain4j panel
modes) was modified beyond adding the third button and the plan-card rendering branch.

## 12. Verified behavior

Tested against the real database and real local Ollama model, not mocked:

| Test | Result |
|---|---|
| "Give every Java developer under Engg a 15% salary hike" (after setting two employees' `role` to "Java Developer" and one to "DevOps Engineer" via the existing update path) | Plan correctly matched only the two Java Developers; math exact (40000 -> 46000, 30000 -> 34500); the DevOps Engineer in the same department correctly excluded |
| Confirming that plan | Both salaries genuinely updated in Postgres (verified via direct `GET /employee/getall`); the untouched employee's salary was unchanged |
| "Give everyone in Hr a 10 percent raise" | Correctly matched the single Hr employee only |
| Cancelling that plan | `executed: false`, summary "Cancelled - no changes were made.", and the employee's salary in the database was confirmed unchanged |
| Unknown/expired `planId` on confirm | `400 Bad Request` with a clear message, not a server error |
| Existing `/employee/*`, `/api/ai/health`, `/api/ai/models`, `/api/ai/chat` | Rechecked working and unaffected after the entity change and new package |
| Angular Agent mode (Playwright) | Plan card renders with correct reasoning and change list; Confirm/Cancel buttons disable and show "Resolved" after use; network calls confirmed hitting `/api/ai/agent/plan` and `/api/ai/agent/confirm` specifically |

## 13. Security considerations

In addition to the general notes in `doc/spring_ai.md` §24 and `doc/function_calling.md` §11:

- **The confirmation gate is the primary safety control, and it is currently unauthenticated.**
  Anyone who can reach `/api/ai/agent/confirm` with a valid `planId` can execute it - there is
  no per-user ownership check on a pending plan beyond matching `conversationId`. Add
  authentication/authorization before any real deployment.
- **`pendingPlans` is a single shared in-memory map with no per-user isolation beyond the
  client-generated `conversationId`.** A guessed or leaked `planId` plus its `conversationId`
  is enough to confirm someone else's plan. Treat conversation ids as sensitive, or add real
  session ownership.
- **The 15-minute plan TTL is a memory-hygiene measure, not a security boundary** - it does not
  re-verify that the matched employees or their data are still current beyond re-fetching by
  id at execute time (§6.3), which does protect against stale *values* but not against the
  *set* of matched employees changing between plan and confirm.
- Bulk salary/role writes should ideally be logged to a durable, queryable audit store for a
  production HR system - today's `[AGENT][...]` log lines are application logs, not a
  compliance-grade audit trail.

## 14. File reference

| File | Responsibility |
|---|---|
| `entity/Employee.java` | Added nullable `role` field |
| `service/EmployeeService.java` | Added `setRole` to the existing `updateEmployee` field copy - required for role writes to persist |
| `ai/tools/EmployeeCrudTools.java` | Added optional `role` parameter to `createEmployee`/`updateEmployee` (unrelated to the agent itself, but needed to populate test data through chat) |
| `ai/agent/EmployeeAgentService.java` | The Reason/Plan/Execute/Summarize orchestrator |
| `ai/agent/dto/AgentPlan.java` | Structured Reason-stage output (LLM-produced) |
| `ai/agent/dto/ProposedChange.java` | One employee's before/after value (used in both the plan preview and the execution result) |
| `ai/agent/dto/AgentPlanRequest.java` / `AgentPlanResponse.java` | `/plan` request/response |
| `ai/agent/dto/AgentConfirmRequest.java` / `AgentExecutionResponse.java` | `/confirm` request/response |
| `ai/agent/controller/EmployeeAgentController.java` | `/api/ai/agent/plan`, `/api/ai/agent/confirm` |
| `ai/agent/controller/EmployeeAgentExceptionHandler.java` | Scoped exception handling, mirrors `AiExceptionHandler` |
| `prompts/employee-agent-plan.st` | Reason-stage prompt template |
| Angular `ai/components/ai-panel/` | Agent provider button, plan-card rendering, confirm/cancel wiring |

No other file was modified to implement this feature.

## 15. Architectural rules

1. The model never writes to the database directly or indirectly through this agent - only
   `EmployeeAgentService`'s own Java code calls `EmployeeService`/`EmployeeRepo` for writes,
   and only from the `confirm(..., true)` path.
2. `plan()` must never mutate any `Employee` row - it is safe to call repeatedly and to
   discard.
3. `confirm()` must re-fetch each employee by id rather than reuse anything cached from
   `plan()`, so execution always reflects the current database state.
4. New action types must be added to `proposeChange` and `applyAction` together, and must stay
   in sync with the `type` values documented in `employee-agent-plan.st` - the prompt and the
   Java switch statements are one contract, not two.
5. The Summarize stage must always be built from the actual applied changes, never from the
   model - this is what makes the final report trustworthy.
6. This feature must not change the request/response contracts of `/api/ai/chat`,
   `/api/ai/langchain/*`, or any `/employee/*` endpoint.
7. Class names in new AI-related packages must be checked against existing simple class names
   across `com.practices.ai.*` before adding a `@Service`/`@Component`/`@Bean` - Spring's
   default bean naming does not disambiguate by package (see §10).

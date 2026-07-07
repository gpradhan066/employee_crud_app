# MCP (Model Context Protocol) Server Guide

## 1. Overview

This document explains the MCP server integration - a sixth, independent AI entry point
alongside Spring AI chat (`doc/spring_ai.md`), Spring AI function calling
(`doc/function_calling.md`), LangChain4j (`doc/langchain4j.md`), the Employee AI Agent
(`doc/agent.md`), and pgvector semantic search (`doc/pgvector.md`).

Every feature documented so far is consumed by **this application's own Angular frontend**,
talking to a REST endpoint this app defines. MCP inverts that: it exposes employee data and
operations to **external AI clients** - Claude Desktop, Cursor, VS Code, the MCP Inspector, or
any other MCP-speaking tool - over a standard protocol, so a general-purpose assistant running
outside this codebase can manage employees without knowing anything about `EmpController` or
`EmployeeService`.

| Concept | Endpoint | Purpose |
|---|---|---|
| MCP server | `POST /mcp` | Single Streamable HTTP endpoint - JSON-RPC 2.0 over HTTP, on the same port (8080) as every other endpoint in this app |
| Tools | 6, listed in §6 | Get/Create/Update/Delete/Search/Statistics on `Employee` - the requirement's exact tool list |
| Resources | 2 static + 1 templated, listed in §7 | Read-only JSON snapshots a client can load without a tool call |
| Prompts | 3, listed in §8 | Reusable natural-language templates that guide a client on how to use the tools |

No existing endpoint, entity, or Angular component was changed to build this - it is entirely
additive, in a new `com.practices.ai.mcp` package that reuses the existing
`EmployeeService`/`EmployeeRepo`.

## 2. Why a separate tool set instead of reusing `EmployeeCrudTools`

`EmployeeCrudTools` (`doc/function_calling.md`) already exposes Employee CRUD as Spring AI
`@Tool` methods - but those are consumed by **this app's own `ChatClient`**, wired in
`AIService`, for the in-app chat feature. MCP tools are consumed by an entirely different
runtime: an external MCP client's model, talking to this app as a server it has never seen
before.

These are two different annotation systems that happen to look similar:

| | `@Tool` (`org.springframework.ai.tool.annotation`) | `@McpTool` (`org.springframework.ai.mcp.annotation`) |
|---|---|---|
| Consumer | This app's own `ChatClient` (in-process function calling) | Any external MCP client, over the wire |
| Registered via | `.tools(employeeCrudTools)` on the `ChatClient` prompt | Auto-scanned by `McpServerAnnotationScannerAutoConfiguration` from any bean |
| Lives in | `com.practices.ai.tools.EmployeeCrudTools` | `com.practices.ai.mcp.tools.EmployeeMcpTools` (new) |

Both classes call the same `EmployeeService`/`EmployeeRepo` underneath - there is exactly one
source of truth for employee data - but keeping them as two separate classes means neither
consumer's tool set can be accidentally changed by editing the other, satisfying "do not change
any existing logic and function."

## 3. High-level architecture

```mermaid
flowchart LR
    EXT[External MCP client<br/>Claude Desktop / Cursor / MCP Inspector] -->|JSON-RPC 2.0 over HTTP<br/>POST /mcp| ROUTER[McpServerStreamableHttpWebMvcAutoConfiguration]

    ROUTER --> TOOLS[EmployeeMcpTools<br/>@McpTool x6]
    ROUTER --> RES[EmployeeMcpResources<br/>@McpResource x3]
    ROUTER --> PROMPTS[EmployeeMcpPrompts<br/>@McpPrompt x3]

    TOOLS --> SVC[EmployeeService]
    TOOLS --> REPO[EmployeeRepo]
    RES --> REPO
    SVC --> REPO
    REPO --> DB[(PostgreSQL emp_db - employee table)]

    SCAN[McpServerAnnotationScannerAutoConfiguration] -.->|scans all beans for<br/>@McpTool/@McpResource/@McpPrompt<br/>at startup| TOOLS
    SCAN -.-> RES
    SCAN -.-> PROMPTS
```

## 4. Package structure

```text
com.practices.ai.mcp
├── tools/
│   └── EmployeeMcpTools.java        6 @McpTool methods - Get/Create/Update/Delete/Search/Statistics
├── resources/
│   └── EmployeeMcpResources.java    3 @McpResource methods - JSON snapshots
└── prompts/
    └── EmployeeMcpPrompts.java      3 @McpPrompt methods - reusable guidance templates
```

No new entity, repository, or controller - MCP tools/resources/prompts are not REST endpoints;
the single `/mcp` route is registered entirely by Spring AI's own auto-configuration, not by any
`@RestController` written for this feature.

## 5. Maven and configuration

One new starter, covered by the existing `spring-ai-bom` import - no new BOM or version
property needed:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

This transitively pulls in `spring-ai-mcp`, `spring-ai-mcp-annotations`, and the MCP Java SDK
(`io.modelcontextprotocol.sdk:mcp-core`) - none of these were on the classpath before.

`application.properties`:

```properties
# MCP (Model Context Protocol) server - exposes employee tools/resources/prompts to
# external AI clients (Claude Desktop, Cursor, MCP Inspector, etc) over Streamable HTTP
# at /mcp on this same app/port. protocol must be set explicitly to STREAMABLE - the
# webmvc auto-configuration's @ConditionalOnProperty does not treat the class-level
# default as "present", so the /mcp route never registers unless this is set.
spring.ai.mcp.server.protocol=STREAMABLE
spring.ai.mcp.server.name=employee-crud-mcp-server
spring.ai.mcp.server.version=1.0.0
spring.ai.mcp.server.instructions=Use get_employee, create_employee, update_employee, delete_employee, \
  employee_search and employee_statistics to manage employee records. Resources employee://all, \
  employee://{id} and employee://statistics provide read-only JSON snapshots.
```

### 5.1 Why `protocol=STREAMABLE` had to be set explicitly - a real bug found and fixed

Spring AI's MCP webmvc auto-configuration supports two HTTP transports: **SSE** (the older,
two-endpoint transport) and **Streamable HTTP** (the current MCP spec's single-endpoint
transport, mounted at `/mcp` by default). `McpServerProperties.protocol` defaults to
`streamable` at the Java property-class level, so it looked safe to leave unset.

Decompiling the auto-configuration's condition classes (there is no public Spring AI reference
doc yet for this exact starter, so the actual `.class` files in the local Maven repo were
inspected directly) showed the real gate:

```java
@ConditionalOnProperty(prefix = "spring.ai.mcp.server", name = "protocol",
        havingValue = "STREAMABLE", matchIfMissing = false)
```

`matchIfMissing = false` means the condition checks whether the property is **literally present
in the environment**, not whether the bound property object's default equals `"STREAMABLE"`.
Since nothing in `application.properties` set it, the condition failed, the Streamable HTTP
router was never registered, and `POST /mcp` 404'd as an unmatched static resource:

```text
No static resource mcp for request '/mcp'.
```

Fixed by setting `spring.ai.mcp.server.protocol=STREAMABLE` explicitly (§5, above) - a one-line,
purely additive property.

### 5.2 A second bug: no `ObjectMapper` bean available

`EmployeeMcpResources` originally took `ObjectMapper` as a constructor parameter, expecting
Spring Boot's usual auto-configured JSON bean. Startup failed instead:

```text
Parameter 0 of constructor in com.practices.ai.mcp.resources.EmployeeMcpResources required a
bean of type 'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

This project depends on `spring-boot-starter-webmvc` (this Spring Boot version's web starter),
which - unlike the classic `spring-boot-starter-web` - does not appear to trigger the usual
Jackson auto-configuration into a bean, even though `jackson-databind` itself is on the
classpath (transitively, via other Spring AI starters) and every existing controller in this
app already serializes JSON responses fine through Spring MVC's own message converter. Rather
than change shared web/Jackson configuration - which risks affecting every other controller in
the app - the fix was scoped entirely to the new class: construct a private `ObjectMapper`
instance directly, instead of injecting one:

```java
private final ObjectMapper objectMapper = new ObjectMapper();
```

## 6. Tools - `EmployeeMcpTools`

File: `src/main/java/com/practices/ai/mcp/tools/EmployeeMcpTools.java`

The 6 tools map directly to the requirement's list:

| Tool name | Method | Behavior |
|---|---|---|
| `get_employee` | `getEmployee(Long id)` | Fetch one employee by id; throws (surfaced as an MCP tool error) if not found |
| `create_employee` | `createEmployee(name, department, salary, performance, experience, role?)` | Same field set as `EmployeeCrudTools.createEmployee`; `role` optional |
| `update_employee` | `updateEmployee(id, name, department, salary, performance, experience, role?)` | Delegates to the existing `EmployeeService.updateEmployee` |
| `delete_employee` | `deleteEmployee(Long id)` | Returns a `DeletionResult(id, deleted, message)` rather than throwing on a missing id |
| `employee_search` | `employeeSearch(String query)` | Case-insensitive partial match across name/department/role/performance; blank query returns everyone |
| `employee_statistics` | `employeeStatistics()` | Count, average/min/max salary, and headcount broken down by department and performance |

Each parameter is annotated with `@McpToolParam(description = "...")`, the MCP-annotation
equivalent of `@ToolParam` - this is what lets an external client's model see a human-readable
description per argument instead of a bare JSON Schema property name. `create_employee` and
`update_employee` mirror `EmployeeCrudTools`'s exact field set (§2) so both tool sets stay
behaviorally consistent even though they are registered independently.

`buildStatistics(List<Employee>)` is a `public static` helper (deliberately public, not
private - `EmployeeMcpResources` calls it directly, §7) so the `employee_statistics` **tool**
and the `employee://statistics` **resource** compute identical numbers from one implementation
rather than two that could drift apart.

## 7. Resources - `EmployeeMcpResources`

File: `src/main/java/com/practices/ai/mcp/resources/EmployeeMcpResources.java`

| URI | Kind | Content |
|---|---|---|
| `employee://all` | Static | JSON array of every employee |
| `employee://statistics` | Static | JSON object - same shape as the `employee_statistics` tool result |
| `employee://{id}` | Templated | JSON detail for a single employee, `{id}` bound from the URI at read time |

Resources exist for a different reason than tools: a tool call is a deliberate, model-initiated
action with a cost (a full round trip); a resource is something a client can load directly into
its context up front - e.g. an IDE assistant pre-loading `employee://all` once at the start of a
session rather than calling `employee_search` repeatedly.

```java
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
    ...
}
```

The `{id}` URI-template variable is bound to the `id` method parameter **by parameter name**,
via reflection - this only works because `spring-boot-starter-parent` already configures
`maven-compiler-plugin` with `<parameters>true</parameters>` by default (verified via
`mvn help:effective-pom`), so real parameter names survive compilation instead of being erased
to `arg0`. No project change was needed for this; it was already in place for other reasons
(constructor-bound `@ConfigurationProperties` records elsewhere in the app rely on the same
flag).

The parameter is typed `String`, not `Long` - URI template variables always arrive as raw path
segments, so the numeric conversion (and its error message on a non-numeric id) is handled
explicitly in the method body rather than assumed.

## 8. Prompts - `EmployeeMcpPrompts`

File: `src/main/java/com/practices/ai/mcp/prompts/EmployeeMcpPrompts.java`

| Prompt name | Arguments | Purpose |
|---|---|---|
| `employee_onboarding` | `name`, `department`, `role?` | Draft a first-week checklist, pointing the client at `create_employee`/`get_employee` |
| `salary_review` | `department` | Guide the client to combine `employee_search` + `employee_statistics` and flag outliers |
| `employee_search_assistant` | `query` | Guide the client to call `employee_search` with a given free-text query and summarize results |

A prompt is not a tool call and not free text either - it is a **template the client asks the
server for**, fills with its own arguments, and then sends onward as if the user had typed it.
Each method returns a `McpSchema.GetPromptResult` built explicitly, rather than relying on the
framework's implicit `String`-to-prompt conversion (which exists and does work, but returns an
`ASSISTANT`-role message - see §8.1), because these prompts are meant to read as instructions
*from* the user *to* an assistant:

```java
private McpSchema.GetPromptResult userPrompt(String text) {
    McpSchema.PromptMessage message =
            new McpSchema.PromptMessage(McpSchema.Role.USER, new McpSchema.TextContent(text));
    return new McpSchema.GetPromptResult(null, List.of(message));
}
```

### 8.1 How this was verified against the actual annotation-processing code, not guessed

`spring-ai-mcp-annotations` ships no reference documentation for this exact starter version, so
its `AbstractMcpPromptMethodCallback.convertToGetPromptResult(Object)` bytecode was decompiled
directly (`javap -c`) to confirm every return type a `@McpPrompt` method is allowed to use:
`GetPromptResult` (used here, for full control over the role), a `List<PromptMessage>`, a single
`PromptMessage`, a `List<String>`, or a single `String` - the last two are auto-wrapped as
`ASSISTANT`-role messages, which is why a plain `String` return was deliberately avoided for
these three prompts.

## 9. Transport - Streamable HTTP on `/mcp`

MCP defines multiple transports; this feature uses **Streamable HTTP**, the current spec's
recommended transport, rather than stdio (a separate process talking over stdin/stdout - the
older Claude Desktop pattern) or SSE (the previous HTTP transport, now superseded). Streamable
HTTP was chosen because:

- It runs **on the same Spring Boot application, same port (8080), same Tomcat instance** as
  every other endpoint in this app - no second process, no extra port to open or secure.
- It works with any HTTP-capable MCP client, including tools that only support HTTP transports
  (many IDE integrations) as well as ones that bridge stdio-only clients to HTTP (e.g.
  `mcp-remote` for Claude Desktop).

A client speaks JSON-RPC 2.0 over `POST /mcp`, starting with `initialize`:

```bash
curl -s -X POST http://localhost:8080/mcp \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl-test","version":"1.0"}}}'
```

The response includes an `Mcp-Session-Id` response header that must be echoed back
(`-H "Mcp-Session-Id: <value>"`) on every subsequent request in the same session
(`tools/list`, `resources/list`, `resources/templates/list`, `prompts/list`, `tools/call`,
`resources/read`, `prompts/get`, etc.).

## 10. Verified behavior

Tested against the real running application, the real Postgres database, and the real MCP
JSON-RPC wire format - not mocked:

| Test | Result |
|---|---|
| `initialize` handshake | `200 OK`, `Mcp-Session-Id` header returned, `serverInfo.name = "employee-crud-mcp-server"`, correct `instructions` text |
| `tools/list` | Exactly 6 tools returned, each with a full JSON Schema `inputSchema` generated from the `@McpToolParam` descriptions |
| `resources/list` | Exactly 2 static resources (`employee://all`, `employee://statistics`) |
| `resources/templates/list` | Exactly 1 template (`employee://{id}`) |
| `prompts/list` | Exactly 3 prompts, each with its `@McpArg`-derived argument list |
| `tools/call employee_statistics` | Returned real aggregate data matching the live database: `{"employeeCount":8,"averageSalary":52821.875,...}` |
| `tools/call get_employee {"id":6}` | Returned the real row for employee 6 (Monika Shinde) as JSON text content |
| `resources/read employee://6` | Returned the same employee's data via the URI-template resource path, confirming `{id}` binding works |
| `prompts/get salary_review {"department":"Engg"}` | Returned a single `USER`-role message correctly referencing the Engg department and the two other tools by name |
| Existing `/employee/getall`, `/api/ai/models`, `/api/ai/semantic-search`, `/api/ai/policy-documents`, `/api/ai/documents` | Rechecked working and unaffected after adding the MCP starter and package |

## 11. A DevTools restart quirk found during development

Spring Boot DevTools' hot in-place restart (triggered automatically on saving a `.java` file
while the app is running) does not reliably reflect a **new dependency jar** added to `pom.xml`
mid-session across repeated soft-restarts within the same long-lived JVM. After several restart
cycles, a class from the newly-added MCP SDK jar
(`io.modelcontextprotocol.spec.McpSchema$Content`) briefly became unresolvable
(`NoClassDefFoundError`) during bean introspection, even though a completely fresh
`mvnw spring-boot:run` process - same code, same `pom.xml` - started cleanly and worked
end-to-end both before and after.

This is a DevTools classloader-partitioning limitation, not a bug in the MCP code or
configuration: **a full process restart is the reliable fix** whenever a new dependency was
added since the JVM was last cold-started. No code change was made for this; it's a
development-workflow note, not a runtime issue that affects deployed behavior.

## 12. Security considerations

In addition to the general notes in `doc/spring_ai.md` §24 and `doc/agent.md` §13:

- **`/mcp` has no authentication or authorization.** Any client that can reach port 8080 can
  call `create_employee`, `update_employee`, or `delete_employee` with no restriction - this is
  a full read/write surface over employee data, exposed to a different class of caller (any
  MCP-speaking tool) than the app's own frontend. Add authentication before any real deployment,
  the same caveat already accepted for every other unauthenticated endpoint in this app today.
- **`delete_employee` and `update_employee` are destructive/irreversible from a single tool
  call**, unlike the Employee AI Agent (`doc/agent.md`), which requires a separate confirm step
  before writing anything. An external MCP client's model can call `delete_employee` directly,
  with no plan-then-confirm gate - this mirrors `EmployeeCrudTools`'s existing behavior
  (`doc/function_calling.md`), not a new risk, but it is worth calling out explicitly since the
  audience (any external AI client) is now much broader than this app's own chat UI.
- **Resources return full employee records verbatim**, including salary - anyone who can read
  `employee://all` or `employee://{id}` sees the same data as `GET /employee/getall`, with no
  field-level redaction.

## 13. File reference

| File | Responsibility |
|---|---|
| `pom.xml` | `spring-ai-starter-mcp-server-webmvc` |
| `application.properties` | `spring.ai.mcp.server.*` - protocol, name, version, instructions |
| `ai/mcp/tools/EmployeeMcpTools.java` | 6 `@McpTool` methods; `buildStatistics` shared with the resources class |
| `ai/mcp/resources/EmployeeMcpResources.java` | 3 `@McpResource` methods, own `ObjectMapper` instance |
| `ai/mcp/prompts/EmployeeMcpPrompts.java` | 3 `@McpPrompt` methods |

No other file was modified to implement this feature.

## 14. Architectural rules

1. `com.practices.ai.mcp.tools.EmployeeMcpTools` must stay independent from
   `com.practices.ai.tools.EmployeeCrudTools` - one is for external MCP clients, the other for
   this app's own `ChatClient`. Do not merge them or have one call the other.
2. Any new employee-derived value exposed as both a tool result and a resource (e.g.
   statistics) must be computed by one shared method, not reimplemented twice.
3. New `@McpResource` URI templates must validate and convert path variables explicitly in the
   method body - they always arrive as `String`, regardless of the target field's real type.
4. `@McpPrompt` methods that represent something the *user* would say must return an explicit
   `McpSchema.GetPromptResult` with `Role.USER` messages, not a bare `String` (which the
   framework wraps as `Role.ASSISTANT` instead - see §8.1).
5. This feature must not change the request/response contracts of `/employee/*` or any existing
   `/api/ai/*` endpoint - MCP is additive, reachable only via `POST /mcp`.

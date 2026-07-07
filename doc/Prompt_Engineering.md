# Prompt Engineering

## Overview
This note documents the prompt engineering implementation for the employee CRUD AI assistant.
The main goal was to avoid hard-coding prompt text inside Java code and move prompt logic into reusable template resources.

## What was implemented
- Replaced inline Java prompts with external `.st` template files.
- Used Spring AI `PromptTemplate` to render prompts at runtime with dynamic values.
- Kept prompt logic in `AIService` while keeping chat orchestration in `AiChatService`.

## Prompt files
The following files were added/used:
- `classpath:/prompts/employee-assistant-system.st`
  - Contains the assistant role definition, rules, and system-level guidance.
  - Receives rendered values for `history` and `context`.
- `classpath:/prompts/employee-assistant-user.st`
  - Contains the user prompt template.
  - Receives the runtime `message` text.
- `classpath:/prompts/employee-assistant-examples.st`
  - Contains example interactions and few-shot guidance for the assistant.

## `AIService` behavior
- The `AIService` class is the prompt engineering layer for the application.
- It renders both system and user prompts from template resources.
- It uses `PromptTemplate` with a map of variables to replace:
  - `history`: formatted prior chat messages.
  - `context`: structured context lines for the assistant.
  - `message`: the current user request.
- It builds a `ChatClient` request with `ChatOptions.builder().model(model)`.
- It supports both normal chat replies (`call().content()`) and streaming replies (`stream().content()`).

## History and context formatting
- History is formatted as text lines such as `USER: ...` and `ASSISTANT: ...`.
- If no history exists, the assistant receives a fallback string: `No previous conversation.`
- Context is rendered as bullet lines when available.
- If no context exists, the assistant receives `No additional context.`

## Service flow
- `AiChatService` validates incoming chat requests and normalizes values.
- Default model selection falls back to `app.ai.default-model` when the request omits a model.
- Conversation IDs are generated automatically when missing.
- Both user and assistant interactions are appended into `ConversationMemory`.
- Streaming support is exposed by `/api/ai/chat/stream`.

## Benefits
- Prompt text is now maintainable and editable without rebuilding Java sources.
- The assistant can evolve by updating `.st` templates only.
- Templates separate system rules from user input, improving clarity.
- The prompt pipeline is reusable across normal and streaming chat flows.

## Related files
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/service/AIService.java`
- `back_end/java/source_code/employee_crud_app/src/main/resources/prompts/employee-assistant-system.st`
- `back_end/java/source_code/employee_crud_app/src/main/resources/prompts/employee-assistant-user.st`
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/service/AiChatService.java`
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/controller/AiController.java`

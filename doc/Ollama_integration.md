# Ollama Integration

## Overview
This note documents the Ollama integration for the employee CRUD AI assistant.
The integration uses Spring AI with Ollama as the default chat model provider.

## What was implemented
- Added a local Ollama health endpoint at `/api/ai/health`.
- Configured Spring AI to select Ollama by default via `spring.ai.model.chat=${AI_PROVIDER:ollama}`.
- Added provider selection infrastructure in `AIConfig`.
- Added model discovery and availability reporting via `AiModelService`.
- Provided robust handling for Ollama base URLs with or without `/v1`.

## Configuration
The application uses Spring Boot properties to configure Ollama:
- `spring.ai.model.chat=ollama` (default provider)
- `spring.ai.ollama.base-url=${OLLAMA_BASE_URL:http://localhost:11434}`
- `spring.ai.ollama.chat.model=${OLLAMA_MODEL:qwen3:1.7b}`
- `spring.ai.ollama.chat.temperature=${OLLAMA_TEMPERATURE:0.2}`
- `spring.ai.ollama.chat.think=${OLLAMA_THINK:false}`
- `spring.ai.ollama.chat.num-predict=${OLLAMA_NUM_PREDICT:256}`

The application also includes properties for alternative providers:
- `spring.ai.openai.api-key`
- `spring.ai.openai.chat.model`
- `spring.ai.anthropic.api-key`
- `spring.ai.anthropic.chat.model`
- `spring.ai.bedrock.aws.region`
- `spring.ai.bedrock.converse.chat.options.model`

## Health endpoint
- `/api/ai/health` returns the Ollama provider status.
- It tests connectivity by calling the Ollama model listing endpoint.
- It normalizes configured base URL values so both `http://localhost:11434` and `http://localhost:11434/v1` work.
- Returns JSON with `status`, `provider`, `baseUrl`, and `message`.

## Model discovery and UI support
- `AiModelService` returns configured provider models and hides local fallback when another external provider is available.
- Unsupported or unconfigured cloud providers are returned as disabled options in the model list.
- The frontend AI assistant dropdown only enables models whose `available` flag is true.

## Provider selection
- `AIConfig` maps the active provider name from `spring.ai.model.chat` to the Spring AI ChatModel bean name.
- The active provider can be switched between `ollama`, `openai`, `anthropic`, or `bedrock-converse`.
- If a selected provider bean is missing, the backend fails fast with a clear startup error.

## Benefits
- Ollama serves as the default local model provider for fast local testing.
- Explicit model availability handling prevents the UI from selecting unavailable models.
- The health endpoint helps verify Ollama connectivity without hitting the chat endpoint.
- Prompt engineering and Ollama setup are decoupled, so prompt logic remains provider-agnostic.

## Related files
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/service/AiHealthService.java`
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/controller/AiController.java`
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/config/AIConfig.java`
- `back_end/java/source_code/employee_crud_app/src/main/resources/application.properties`
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/service/AiModelService.java`
- `back_end/java/source_code/employee_crud_app/src/main/java/com/practices/ai/provider/OpenAiCompatibleProvider.java`

# Data Model: Startup Self-Check & Config Initialization

## Key Entities

### Configuration Entity
This entity represents the persistent state of the Ganglia agent's configuration, typically stored in `.ganglia/config.json`.

| Field | Type | Description |
|-------|------|-------------|
| `agent` | `AgentConfig` | General parameters like `maxIterations`, `compressionThreshold`, and `projectRoot`. |
| `models` | `Map<String, ModelConfig>` | Mapping of model usage roles (e.g., `primary`, `utility`) to their respective configurations. |
| `observability` | `ObservabilityConfig` | Settings for tracing and logging. |
| `webui` | `WebUIConfig` | Configuration for the WebUI interface (port, enabled status). |

### ModelConfig Detail
- `name`: String (Model name like "gpt-4o")
- `temperature`: Double (0.0 to 1.0)
- `maxTokens`: Integer
- `contextLimit`: Integer (Total context window size)
- `type`: String ("openai", "anthropic", "gemini")
- `apiKey`: String (Sensitive)
- `baseUrl`: String (Endpoint URL)
- `stream`: Boolean (Enable streaming response)

## Persistence
- **Storage Strategy**: File-based (JSON)
- **Primary Location**: `.ganglia/config.json`
- **Fallback**: Defaults provided by `ConfigManager.java`.

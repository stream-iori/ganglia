# Research: Startup Self-Check & Config Initialization

## Decision: Integrate into ConfigManager

Based on the investigation of `Main.java` and `ConfigManager.java`, the best place to implement the startup self-check and configuration initialization is within the `ConfigManager` class.

### Rationale
- `ConfigManager` is already responsible for loading the configuration and provides defaults via `getDefaultConfig()`.
- It is initialized early in the `Main.bootstrap` process.
- By adding the logic here, we ensure that all entry points (Terminal, WebUI, Tests) benefit from the self-check.

### Technical Approach
1.  **Non-blocking FS Operations**: Use Vert.x `fileSystem()` to check for directory/file existence and create them if missing.
2.  **Initialization Trigger**: Call an `ensureConfigExists()` method inside `ConfigManager.init()`.
3.  **Default Content**: Use the existing `getDefaultConfig()` to populate the new file.
4.  **User Notification**: Log a clear info message when a new configuration is initialized.

## Decision: Vert.x FileSystem API

We will use the non-blocking Vert.x FileSystem API to maintain the reactive nature of the project.

### Rationale
- Consistent with the "Reactive & Non-blocking" core principle.
- Avoids blocking the event loop during bootstrap.

### Alternatives Considered
- **Java NIO `Files.write`**: Rejected because it's blocking and doesn't align with Vert.x best practices within this project.
- **`ConfigRetriever` with file creation**: `ConfigRetriever` is for loading, not creating. Mixing concerns would be less clean than a dedicated `ensureConfigExists` method.

## Decision: Configuration Schema

The schema will match the existing `GangliaConfig` and `ModelConfig` classes, initialized with the values from `ConfigManager.getDefaultConfig()`.

### Rationale
- Ensures compatibility with existing code.
- Provides a working baseline with standard LLM placeholders.

## Research Task: Permission Handling
- Vert.x `mkdirs` and `writeFile` will return failed futures if permissions are missing.
- We will catch these failures and provide a clean error message to the user, satisfying **FR-005**.

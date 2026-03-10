# Feature Specification: Startup Self-Check & Config Initialization

**Feature Branch**: `001-startup-self-check`  
**Created**: 2026-03-10  
**Status**: Draft  
**Input**: User description: "我希望建立一个项目启动时的自检能力，比如检查.ganglia/config.json 是否存在，不存在则创建基本的config.json."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Automatic Configuration Initialization (Priority: P1)

As a new user of the system, I want the application to automatically set up its base configuration on first run so that I can start using it immediately without manual setup.

**Why this priority**: Essential for first-run experience and system stability. Ensures a baseline working state.

**Independent Test**: Remove the configuration directory/file and launch the system; verify the baseline configuration is created and the system starts.

**Acceptance Scenarios**:

1. **Given** no configuration file exists, **When** the system starts, **Then** a default configuration file is created in the expected location.
2. **Given** a configuration file already exists, **When** the system starts, **Then** the existing file is preserved and not overwritten.

---

### User Story 2 - Startup Validation Feedback (Priority: P2)

As a user, I want to be informed when the system performs self-checks or auto-initialization so that I am aware of changes made to my environment.

**Why this priority**: Provides transparency and avoids confusion about "hidden" file creation.

**Independent Test**: Run the system for the first time; verify that a clear message is displayed/logged indicating the configuration was missing and has been initialized.

**Acceptance Scenarios**:

1. **Given** the configuration was auto-created, **When** initialization completes, **Then** the user sees a notification or log entry describing the action.

---

### Edge Cases

- **Read/Write Permissions**: What happens if the system lacks permission to create the directory or file?
- **Corrupted Structure**: What if the directory exists but the file is missing or vice versa?
- **Concurrent Startup**: How does the system handle multiple instances starting simultaneously for the first time? (Assumed out of scope for MVP, default to standard file creation).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST check for the presence of the `.ganglia` directory and `config.json` file at startup.
- **FR-002**: System MUST create a default `.ganglia/config.json` file if it is not found.
- **FR-003**: System MUST populate the new configuration file with a Standard LLM Configuration, including placeholders for API keys, model names, and base URL for a typical agent setup.
- **FR-004**: System MUST notify the user via logs or console output when a new configuration has been initialized.
- **FR-005**: System MUST fail gracefully with a clear error message if the self-check identifies a critical environment issue (e.g., read-only filesystem).

### Key Entities

- **Configuration File**: Represents the persistent settings for the application. Key attributes include version, storage location, and baseline parameters.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of first-time runs result in a valid configuration file being present before the main application logic executes.
- **SC-002**: User is notified within 1 second of startup if an auto-initialization event occurred.
- **SC-003**: System startup continues successfully after auto-initialization without requiring a manual restart.
- **SC-004**: No existing user configuration is ever overwritten by the self-check process.

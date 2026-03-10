---

description: "Task list template for feature implementation"
---

# Tasks: Startup Self-Check & Config Initialization

**Input**: Design documents from `/specs/001-startup-self-check/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Test tasks included to verify configuration persistence and non-overwrite behavior.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `ganglia-core/src/main/`, `ganglia-core/src/test/`

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Verify Hexagonal Layer alignment (Infrastructure/FileSystem separation)
- [x] T002 Ensure project root configuration (.ganglia) is accessible

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure for non-blocking file operations in ConfigManager

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T003 Implement `ensureConfigExists()` method signature in `ganglia-core/src/main/java/work/ganglia/config/ConfigManager.java`
- [x] T004 Call `ensureConfigExists()` inside `init()` method in `ganglia-core/src/main/java/work/ganglia/config/ConfigManager.java`
- [x] T005 Setup integration/unit test skeleton for self-check in `ganglia-core/src/test/java/work/ganglia/config/ConfigManagerTest.java`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Automatic Configuration Initialization (Priority: P1) 🎯 MVP

**Goal**: Automatically create default `.ganglia/config.json` if missing.

**Independent Test**: Delete `.ganglia/config.json`, start app, verify file is created with default LLM content.

### Implementation for User Story 1

- [x] T006 [US1] Implement non-blocking directory check/creation (`.ganglia`) in `ConfigManager.ensureConfigExists` using Vert.x `fileSystem().mkdirs()`
- [x] T007 [US1] Implement default configuration persistence logic in `ConfigManager.ensureConfigExists` using `getDefaultConfig()` and `fileSystem().writeFile()`
- [x] T008 [US1] Add logic to prevent overwriting existing configuration in `ConfigManager.ensureConfigExists`
- [x] T009 [US1] Add test case `testAutoInitialization()` in `ConfigManagerTest.java` to verify file creation
- [x] T010 [US1] Add test case `testNoOverwrite()` in `ConfigManagerTest.java` to verify existing file preservation

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Startup Validation Feedback (Priority: P2)

**Goal**: Notify user when config is initialized or when errors occur.

**Independent Test**: Start app first time, check console for "Initializing new configuration" message.

### Implementation for User Story 2

- [x] T011 [P] [US2] Add INFO logging in `ConfigManager.ensureConfigExists` when a new configuration file is initialized
- [x] T012 [P] [US2] Implement robust error handling for FS permission issues in `ConfigManager.ensureConfigExists` with clear ERROR logging (FR-005)
- [x] T013 [US2] Verify initialization logs in `ConfigManagerTest.java` (using log interceptor if available, or manual verification)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T014 Run `quickstart.md` validation on a fresh environment
- [x] T015 Perform code cleanup and ensure all Vert.x future chains are properly handled in `ConfigManager.java`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User Story 1 (P1) is the MVP and should be completed first.
  - User Story 2 (P2) can be implemented in parallel with US1's polish.
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### Parallel Opportunities

- All tasks marked [P] can run in parallel (T011, T012).
- Unit test cases for different scenarios (T009, T010) can be drafted in parallel.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently (delete `.ganglia/config.json`, verify creation).

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → MVP Ready!
3. Add User Story 2 → Test feedback loop.

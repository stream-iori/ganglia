---

description: "Task list template for feature implementation"
---

# Tasks: Refactor Observation Architecture

**Input**: Design documents from `/specs/003-refactor-observation-arch/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Verify Hexagonal Layer alignment (ensure ports/internal/state is the right place for ExecutionContext)
- [x] T002 Create `work.ganglia.port.internal.state.ExecutionContext` interface with `sessionId()`, `emitStream(String)`, `emitError(Throwable)` methods
- [x] T003 Create `work.ganglia.port.internal.state.ObservationDispatcher` interface

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Create `work.ganglia.kernel.loop.DefaultObservationDispatcher` implementing both `ObservationDispatcher` and `AgentLoopObserver`
- [x] T005 Implement EventBus publishing logic inside `DefaultObservationDispatcher` to target both session-specific and `ADDRESS_OBSERVATIONS_ALL` topics
- [x] T006 Update `StandardAgentLoop` constructor to accept `ObservationDispatcher` instead of a list of `AgentLoopObserver`
- [x] T007 Replace direct `observers.forEach(...)` calls in `StandardAgentLoop` with calls to the injected `ObservationDispatcher`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 2 - Decoupled Tool Implementation (Priority: P2)

**Goal**: Remove direct Vert.x dependencies from Tools and Gateways. (Doing this first as it unblocks WebUI integration).

### Implementation for User Story 2

- [x] T008 [US2] Update `work.ganglia.port.external.tool.ToolDefinition.execute` interface to accept `ExecutionContext context` as an argument
- [x] T009 [US2] Update `work.ganglia.infrastructure.external.tool.BashTools` to use `context.emitStream()` for TTY output instead of `vertx.eventBus().publish()`
- [x] T010 [US2] Update all other implementations of `ToolDefinition` to adhere to the new `execute(ExecutionContext, ...)` signature
- [x] T011 [US2] Update `work.ganglia.kernel.task.Schedulable` interface and its implementations (e.g., `ToolCallTask`, `SubAgentTask`) to accept and pass down an `ExecutionContext`
- [ ] T012 [US2] Update `work.ganglia.port.external.llm.ModelGateway.chatStream` to accept `ExecutionContext` or a specific token consumer callback
- [ ] T013 [US2] Update `work.ganglia.infrastructure.external.llm.AbstractModelGateway` and implementations (`OpenAIModelGateway`, `AnthropicModelGateway`) to use the new callback/context for `TOKEN_RECEIVED` events instead of direct EventBus publishing
- [x] T014 [US2] Wire the `ExecutionContext` instantiation inside `StandardAgentLoop` or `DefaultSchedulableFactory` to link back to the `ObservationDispatcher`

**Checkpoint**: Tools and Gateways are fully decoupled from Vert.x EventBus

---

## Phase 4: User Story 1 - Unified Streaming to WebUI (Priority: P1)

**Goal**: WebUI receives tokens and TTY streams correctly.

### Implementation for User Story 1

- [ ] T015 [US1] Remove `AgentLoopObserver` interface implementation from `work.ganglia.web.WebUIEventPublisher`
- [ ] T016 [US1] Refactor `WebUIEventPublisher` to act as an EventBus consumer listening on `Constants.ADDRESS_OBSERVATIONS_ALL`
- [ ] T017 [US1] Implement event routing logic in `WebUIEventPublisher` to filter and translate incoming `ObservationEvent` payloads into `ServerEvent` WebSocket messages
- [ ] T018 [US1] Update `work.Main` (Bootstrap) to instantiate `DefaultObservationDispatcher` and register `WebUIEventPublisher` as a consumer instead of an observer list
- [ ] T019 [US1] Update `work.ganglia.example.WebUIDemo` to reflect bootstrap changes
- [ ] T020 [US1] Verify that `TraceManager` still correctly records all events without duplication

**Checkpoint**: WebUI correctly receives all events from the unified stream

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T021 Run full integration test suite (`mvn test`)
- [ ] T022 Clean up deprecated methods or unused EventBus topic constants
- [ ] T023 Update developer documentation regarding how to write new Tools with `ExecutionContext`

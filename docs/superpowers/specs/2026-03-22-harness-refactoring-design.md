# Ganglia Harness Refactoring Plan

## Background & Motivation

The `ganglia-harness` module exhibits excellent adherence to the Dependency Inversion Principle, but analysis revealed significant violations of the Single Responsibility Principle (SRP) and the DRY (Don't Repeat Yourself) principle.

1. **Circular Dependency Hack**: `GangliaKernel` currently uses an inner proxy class (`LazyTaskFactoryProxy`) and redundant object instantiations to resolve a circular dependency between `AgentLoopFactory` and `AgentTaskFactory`.
2. **God Object (`ConfigManager`)**: The `ConfigManager` is doing too much—file watching, JSON merging, state management, and implementing multiple interfaces. It also contains repetitive boilerplate for safely retrieving nested configuration values.

## Scope & Impact

This refactoring focuses strictly on:
- `work.ganglia.kernel.GangliaKernel` (dependency assembly)
- `work.ganglia.config.ConfigManager` and new associated classes (configuration loading)
- Safe, non-breaking changes to internal wiring without modifying external APIs or the fundamental `BootstrapOptions`.

## Proposed Solution

### Phase 1: Break Circular Dependency in `GangliaKernel`

We will leverage the existing `AgentEnv` as a late-binding mechanism to supply the `AgentTaskFactory` to the `AgentLoopFactory`.

**Implementation Steps:**
1. Remove `LazyTaskFactoryProxy` from `GangliaKernel.java`.
2. Remove the duplicate block creating `finalLoopFactory`, `finalTaskFactory`, and `finalGraphExecutor`.
3. Reorder the assembly logic:
- Build `AgentEnv` *before* the loop factory, leaving its `taskFactory` null.
- Define `AgentLoopFactory` such that it fetches the `taskFactory` from the `env` reference when `createLoop()` is called.
- Instantiate `DefaultAgentTaskFactory` passing in the loop factory.
- Inject the task factory back into `AgentEnv`, `PromptEngine`, and `GraphExecutor`.

### Phase 2: Refactor ConfigManager (SRP & DRY)

We will split the responsibilities of file I/O from state representation, and introduce functional helpers for data access.

**Implementation Steps:**
1. **DRY Helpers**: Within the existing `ConfigManager` structure, introduce a generic private helper method `getModelProp(String modelKey, Function<ModelConfig, T> getter, T defaultVal)` using `Optional`.
2. **Apply Helpers**: Refactor all `ModelConfigProvider` interface methods (e.g., `getTemperature()`, `getMaxTokens()`) to use this helper, eliminating repetitive null checks.
3. **Split Responsibilities (Optional/Subsequent)**:
- Rename/extract the file-loading and Vert.x `ConfigRetriever` logic into a new class `ConfigFileWatcher`.
- Leave `ConfigManager` as the pure registry implementing the Provider interfaces.
- *Note: For this iteration, we will focus on extracting the Vert.x logic into `ConfigFileWatcher` while leaving `ConfigManager` as the primary interface provider.*

## Verification & Testing

1. Compile the project: `mvn clean install -DskipTests` (or equivalent via `just`).
2. Run the integration tests/simulations to ensure the agent still boots up successfully and the loop can process tasks. (The framework heavily relies on `ConfigManager` and `GangliaKernel` for bootstrapping, so any failure here will be immediately obvious in tests).
3. Manually verify via `just ui-watch` or `WebUIDemo` that the system starts without proxy errors.

## Migration & Rollback

Since these changes are entirely internal to the wiring and configuration implementation, rollback involves simply reverting the git commit. No database schemas or external configuration files are changing format.

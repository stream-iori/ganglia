<!--
Sync Impact Report:
- Version change: [CONSTITUTION_VERSION] → 1.4.0
- List of modified principles:
  - PRINCIPLE_1: [PRINCIPLE_1_NAME] → I. Hexagonal (Ports & Adapters) Architecture
  - PRINCIPLE_2: [PRINCIPLE_2_NAME] → II. Reactive & Non-blocking (Vert.x 5)
  - PRINCIPLE_3: [PRINCIPLE_3_NAME] → III. Memory as Code
  - PRINCIPLE_4: [PRINCIPLE_4_NAME] → IV. Single ReAct Control Loop
  - PRINCIPLE_5: [PRINCIPLE_5_NAME] → V. Native LLM Gateways (No SDKs)
  - Added: VI. Tool-First Navigation
- Added sections: Communication Protocol, Development Workflow
- Removed sections: None
- Templates requiring updates:
  - .specify/templates/plan-template.md (✅ updated)
  - .specify/templates/spec-template.md (✅ checked - no changes needed)
  - .specify/templates/tasks-template.md (✅ updated)
- Follow-up TODOs: None
-->

# Ganglia Constitution

## Core Principles

### I. Hexagonal (Ports & Adapters) Architecture
The system MUST maintain strict separation between layers:
- **Kernel**: Pure reasoning and orchestration logic. No technical dependencies.
- **Port**: Strict interfaces and domain models.
- **Infrastructure**: Technical implementations (LLM, FS, Vert.x).
All dependencies MUST flow inward toward the Kernel and Ports.

### II. Reactive & Non-blocking (Vert.x 5)
The framework is built on Vert.x 5. All I/O operations MUST be non-blocking. 
- Use `Future` or `Promise` for asynchronous coordination.
- Never block the event loop with synchronous calls (Thread.sleep, blocking I/O).
- Ensure high-concurrency safety using Vert.x verticle isolation.

### III. Memory as Code
Project and session memory MUST be transparent and file-based:
- **Long-term**: `MEMORY.md` at the root.
- **Medium-term**: Daily Journals in `.ganglia/memory/daily-*.md`.
- Memory is version-controlled and human-editable Markdown.

### IV. Single ReAct Control Loop
Avoid complex, opaque multi-agent graphs for core reasoning.
- Use a flat message history processed by a single main loop.
- Flow: `Input -> [Thought -> Tool -> Observation] * N -> Answer`.
- Sub-agents are treated as high-level tools, not independent state machines.

### V. Native LLM Gateways (No SDKs)
The system MUST NOT depend on heavy third-party LLM SDKs (e.g., LangChain, OpenAI-Java).
- Protocols (OpenAI, Anthropic) MUST be implemented natively using Vert.x `WebClient`.
- This ensures transparency, minimal footprint, and direct control over SSE parsing.

### VI. Tool-First Navigation
The agent MUST explore codebases using tools (`grep_search`, `glob`, `read_file`) rather than relying purely on pre-computed vector embeddings.
- This ensures "groundedness" in the current state of the filesystem.

## Communication Protocol
Ganglia uses **JSON-RPC 2.0** over **WebSockets** for all external client communication.
- All requests MUST follow the JSON-RPC structure.
- Asynchronous events (thoughts, tool starts) are pushed as notifications.

## Development Workflow
Development tasks MUST be managed using the `just` command runner.
- `just setup`: Initialize environment.
- `just test`: Run full suite (Backend & Frontend).
- UI prototyping SHOULD use **Mock Mode** for protocol verification.
- Backend-served assets SHOULD use **Linked Debugging** (`just ui-watch` + `just backend`).

## Governance
This Constitution is the foundational governing document for the Ganglia project and supersedes all other practices.
- All Pull Requests and design reviews MUST verify compliance with these principles.
- Amendments to this constitution require a version bump and updated Sync Impact Report.

**Version**: 1.4.0 | **Ratified**: 2026-03-10 | **Last Amended**: 2026-03-10

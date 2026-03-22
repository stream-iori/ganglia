# Fix Java Coding Convention Violations Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 'NeedBraces' and 'AvoidStarImport' violations in Java files to adhere to standard coding conventions.

**Architecture:** Surgical replacement of star imports with explicit imports and wrapping single-line control flow blocks in braces.

**Tech Stack:** Java, Grep, Sed/Replace tools.

---

### Task 1: Fix AvoidStarImport Violations

**Files:**
- Modify: `ganglia-web/src/main/java/work/ganglia/web/WebUiVerticle.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/kernel/subagent/ContextScoper.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/external/llm/OpenAiModelGateway.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/external/llm/AnthropicModelGateway.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/kernel/todo/ToDoTools.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/mcp/VertxMcpClient.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/kernel/GangliaKernel.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/internal/prompt/StandardPromptEngine.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/internal/prompt/context/MarkdownContextResolver.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/internal/skill/DefaultSkillService.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/internal/memory/FileSystemMemoryStore.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/infrastructure/internal/state/DefaultSessionManager.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/port/chat/SessionContext.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/port/internal/skill/SkillManifest.java`
- Modify: `ganglia-harness/src/main/java/work/ganglia/port/internal/memory/MemoryStore.java`
- Modify: `ganglia-harness/src/test/java/work/ganglia/infrastructure/internal/memory/FileSystemMemoryStoreTest.java`
- Modify: `ganglia-terminal/src/main/java/work/ganglia/ui/MarkdownRenderer.java`

- [ ] **Step 1: Identify used classes for each star import**
- [ ] **Step 2: Replace star imports with explicit imports**
- [ ] **Step 3: Verify compilation**

### Task 2: Fix NeedBraces Violations

**Files:**
- All files identified by grep search in previous research.

- [ ] **Step 1: Wrap single-line 'if', 'else', 'for', 'while', 'do' statements in braces**
- [ ] **Step 2: Ensure correct indentation and formatting**
- [ ] **Step 3: Verify compilation and run tests**

### Task 3: Final Verification

- [ ] **Step 1: Run a final grep to ensure no violations remain**
- [ ] **Step 2: Run all project tests**


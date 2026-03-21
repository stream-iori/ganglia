# Frontend Mock Protocol Documentation

This document describes the JSON-RPC 2.0 protocol scenarios covered by the frontend **Mock Mode** (`?mock` URL parameter).

## Quick Start

1. Start the development server: `cd ganglia-webui && npm run dev`
2. Open Chrome and navigate to: `http://localhost:5173/?mock`
3. Open Chrome DevTools (**F12**) and watch the **Console** for `[MockWS]` logs to see the underlying JSON-RPC traffic.

---

## Step-by-Step Verification Guide

### 1. Connection & Session Initialization

- **Action**: Simply load the URL.
- **Observation**:
  - The Sidebar immediately shows the workspace path as `/mock/workspace`.
  - The Main Stream shows a welcome message: "Hello! I am a mock agent...".
  - **Protocol**: Triggers `SYNC` request and receives `INIT_CONFIG` notification.

### 2. File Exploration & Inspection

- **Action**: Click the `src` folder in the sidebar, then click `main.ts`.
- **Observation**:
  - The right **Inspector** drawer slides out.
  - It shows a mock TypeScript file with syntax highlighting.
  - Try clicking `App.vue` to see HTML/Vue highlighting.
  - **Protocol**: Triggers `LIST_FILES` and `READ_FILE`.

### 3. Agent Execution (Typewriter & Code)

- **Action**: Type `hello` in the bottom input box and press **Enter**.
- **Observation**:
  - Text appears character-by-character (Typewriter effect).
  - A TypeScript code block appears at the end of the message.
  - **Protocol**: Triggers `START` request and multiple `TOKEN` notifications.

### 4. Tool Execution & Terminal Logs

- **Action**: Type `run ls` and press **Enter**.
- **Observation**:
  - A **Tool Card** appears with a pulse animation.
  - The top Status Bar switches to **EXECUTING**.
  - Click the **Logs** button inside the card.
  - The Inspector opens in **Terminal** mode, showing real-time terminal output lines.
  - **Protocol**: Triggers `TOOL_START` -> `tty_event` streams -> `TOOL_RESULT`.

### 5. Interaction & Decision (Authorization)

- **Action**: Type `ask` or `diff` and press **Enter**.
- **Observation**:
  - A full-screen **Authorization Required** dialog appears.
  - It contains a code **Diff** preview.
  - The bottom input area is **disabled** (Blocked state).
  - **Action**: Click the green button "**Yes, apply it**".
  - **Result**: The dialog closes, the input area re-enables, and the Agent sends a follow-up message.
  - **Protocol**: Triggers `ASK_USER` notification and `RESPOND_ASK` request.

### 6. Task Control (Cancel)

- **Action**: Type `run ls` again. While the terminal lines are still streaming, click the red **Cancel** button in the top **Status Bar**.
- **Observation**:
  - The execution immediately stops.
  - The Agent sends a confirmation: "Execution cancelled by user."
  - The UI returns to **IDLE** state.
  - **Protocol**: Triggers `CANCEL` request.

### 7. Error Handling & Recovery

- **Action**: Type `trigger error` and press **Enter**.
- **Observation**:
  - A red **System Error** card appears.
  - **Action**: Click the **Retry Last Command** button inside the error card.
  - **Result**: The Agent attempts the command again (re-triggers the flow).
  - **Protocol**: Triggers `SYSTEM_ERROR` notification and `RETRY` request.

---

## Technical Implementation

The mock logic is encapsulated in `src/services/eventbus.ts`. It works by intercepting the `window.WebSocket` constructor and providing a stateful `MockWebSocket` class that responds to JSON-RPC methods according to the `params.prompt` content.

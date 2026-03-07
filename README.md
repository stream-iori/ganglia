# Ganglia

**Ganglia** is a high-performance, non-blocking Java 17 Agent framework built on **Vert.x 5.0.6**. It follows a **Hexagonal (Ports & Adapters)** architecture, prioritizing simplicity, robustness, and transparency for building autonomous agents that can be integrated into any third-party application.

![Status](https://img.shields.io/badge/status-Implemented-success)
![Version](https://img.shields.io/badge/version-1.3.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Vert.x](https://img.shields.io/badge/Vert.x-5.0.6-purple)

---

## 🚀 Key Features

- **Single Control Loop (ReAct):** A powerful, flat reasoning loop inspired by Claude Code.
- **Hexagonal Architecture:** Complete decoupling of core reasoning (Kernel) from model providers and technical implementations (Infrastructure).
- **Native LLM Gateways:** Native support for OpenAI and Anthropic protocols via Vert.x WebClient. **No third-party SDK dependencies.**
- **Memory as Code:** Transparent, file-based memory system using Markdown (`MEMORY.md` and Daily Journals).
- **Standard-based Communication:** Standard **WebSockets** and **JSON-RPC 2.0** for all client-server interactions, supporting multi-platform clients.
- **Real-time File Monitoring:** Integrated recursive file system monitoring using JDK `WatchService` with debounced UI synchronization.
- **Asynchronous & Reactive:** Built entirely on Vert.x for high-concurrency, non-blocking operations.
- **Modern WebUI:** A reactive dashboard built with Vue 3 and Tailwind CSS for real-time monitoring, interaction, and high-fidelity code/diff review.

---

## ⚡ Quick Start with `just`

The project uses the `just` command runner for common development tasks.

| Command | Description |
| :--- | :--- |
| `just setup` | Initialize everything (Maven install + NPM install) |
| `just backend` | Start the Java Backend (Interactive WebUI Demo) |
| `just frontend` | Start the Frontend Dev Server (Vite) |
| `just ui-watch` | Build UI in watch mode (updates `dist/` continuously) |
| `just build-all` | Full production build (UI + Backend JAR) |
| `just test` | Run all tests (Backend & Frontend) |

---

## 🛠 Development Workflow

### Mock Mode (No Backend Required)
For rapid UI prototyping and protocol verification, you can run the frontend in **Mock Mode**:
1. Start the dev server: `just frontend`
2. Open **`http://localhost:5173/?mock`**
3. See [MOCK-PROTOCOL.md](ganglia-webui/docs/MOCK-PROTOCOL.md) for verification steps.

### Linked Debugging (Recommended)
To see frontend changes reflected in the backend-served UI immediately:
1. **Terminal 1**: `just ui-watch` (keeps `dist/` updated).
2. **Terminal 2**: `just backend` (serves assets from `dist/`).
3. Open `http://localhost:8080`.

---

## 🏗 Architecture

Ganglia is organized into four hexagonal layers:

1.  **API / Adapter Layer**: Entry points like `WebUIVerticle` (WebSockets) and `TerminalUI` (Console).
2.  **Kernel Layer**: The heart of the reasoning loop, task scheduling, and state evolution.
3.  **Port Layer**: Domain models (`Message`, `Turn`, `Context`) and strict service interfaces.
4.  **Infrastructure Layer**: Implementation details (LLM Gateways, File System, Persistence).

For more details, see the [Architecture Documentation](docs/ARCHITECTURE.md).

---

## 🔌 Communication Protocol

Ganglia uses **JSON-RPC 2.0** over **WebSockets** for all external client communication.

### Client-to-Server Methods
- **`SYNC`**: Request full session history and workspace configuration.
- **`START`**: Begin a new reasoning turn with a natural language prompt.
- **`LIST_FILES`**: Manually request a fresh workspace file tree snapshot.
- **`READ_FILE`**: Retrieve the content of a specific file (with syntax highlighting metadata).
- **`RESPOND_ASK`**: Provide user authorization or selection for an interactive tool.
- **`CANCEL`**: Abort current agent execution and tool tasks.
- **`RETRY`**: Re-trigger the last failed or interrupted turn.

### Server-to-Client Notifications
- **`server_event`**: Unified stream for thoughts, tool starts, results, and system errors.
- **`tty_event`**: High-frequency streaming output for shell commands.

---

## 📜 License

© 2026 Ganglia Team. All rights reserved.

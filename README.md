# Ganglia

**Ganglia** is a high-performance, non-blocking Java 17 Agent framework built on **Vert.x 5.0.6**. It follows a **Hexagonal (Ports & Adapters)** architecture, prioritizing simplicity, robustness, and transparency for building autonomous agents that can be integrated into any third-party application.

![Status](https://img.shields.io/badge/status-Implemented-success)
![Version](https://img.shields.io/badge/version-1.2.0-blue)
![Java](https://img.shields.io/badge/Java-17-orange)
![Vert.x](https://img.shields.io/badge/Vert.x-5.0.6-purple)

---

## 🚀 Key Features

- **Single Control Loop (ReAct):** A powerful, flat reasoning loop inspired by Claude Code.
- **Hexagonal Architecture:** Complete decoupling of core reasoning (Kernel) from model providers and technical implementations (Infrastructure).
- **Native LLM Gateways:** Native support for OpenAI and Anthropic protocols via Vert.x WebClient. **No third-party SDK dependencies.**
- **Memory as Code:** Transparent, file-based memory system using Markdown (`MEMORY.md` and Daily Journals).
- **Dynamic Skill System:** Support for both script-based (Node, Python, Bash) and JAR-based Java skills with ClassLoader isolation.
- **Asynchronous & Reactive:** Built entirely on Vert.x for high-concurrency, non-blocking operations.
- **Modern WebUI:** A reactive dashboard built with Vue 3 and Tailwind CSS for real-time monitoring and interaction.

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

### Linked Debugging (Recommended)
To see frontend changes reflected in the backend-served UI immediately:

1. **Terminal 1**: Start the frontend watch mode. This keeps `ganglia-webui/dist` up to date.
   ```bash
   just ui-watch
   ```
2. **Terminal 2**: Start the backend. It will serve assets from the `dist` folder.
   ```bash
   just backend
   ```
3. Open `http://localhost:8080` in your browser.

### Hot Module Replacement (HMR)
For the fastest feedback loop during Vue component development:
1. Start the backend (`just backend`).
2. Start the dev server (`just frontend`).
3. Open **`http://localhost:5173`**.
   - *Note*: The frontend connects to the backend at 8080 via CORS.

---

## 🐞 Troubleshooting

### CORS Rejected / Invalid Origin
- Ensure you are accessing either `http://localhost:5173` or `http://localhost:8080`.
- The `WebUIVerticle` is configured to allow local development origins.

### HMR Not Working
- Check `ganglia-webui/vite.config.ts`. It uses `usePolling: true` to ensure compatibility across various file systems.
- Ensure you see `[vite] connected.` in the browser console.

---

## 🏗 Architecture

Ganglia is organized into four hexagonal layers:

1.  **API / Adapter Layer**: Entry points like `WebUIVerticle` and `TerminalUI`.
2.  **Kernel Layer**: The heart of the reasoning loop and task scheduling.
3.  **Port Layer**: Domain models (`Message`, `Context`) and service interfaces.
4.  **Infrastructure Layer**: Implementation details (Native LLM Gateways, File State, Tools).

For more details, see the [Architecture Documentation](docs/ARCHITECTURE.md).

---

## 🔌 EventBus Protocol

Ganglia uses the Vert.x EventBus for internal and external communication. Constants are defined in `work.ganglia.util.Constants`.

- **`ganglia.ui.req`**: Inbound commands (START, STOP, REFRESH).
- **`ganglia.ui.stream.{sessionId}`**: Outbound state updates, thoughts, and tokens.
- **`ganglia.ui.stream.{sessionId}.tty`**: Real-time shell output bypass.

---

## 📜 License

© 2026 Ganglia Team. All rights reserved.

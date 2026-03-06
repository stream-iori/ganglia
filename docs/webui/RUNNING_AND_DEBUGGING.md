# Ganglia WebUI Running & Debugging Guide

This guide provides instructions on how to start, develop, and debug the Ganglia WebUI system.

## 🛠 Prerequisites

- **Java 17+** (JDK 17-zulu recommended via SDKMAN)
- **Node.js 18+** & **npm**
- **Maven 3.9+**
- **just** (Command runner, optional but recommended)

---

## ⚡ Quick Start with `just`

The easiest way to manage the project is using the `just` command runner from the root directory.

| Command | Description |
| :--- | :--- |
| `just setup` | Initialize everything (Maven install + NPM install) |
| `just backend` | Start the Java Backend (Interactive CLI + WebUI Bridge) |
| `just frontend` | Start the Frontend Dev Server (Vite) |
| `just build-ui` | Build UI and sync to Java resources (webroot) |
| `just build-all` | Full production build (UI + Backend JAR) |
| `just test` | Run both Backend and Frontend tests |

---

## 🚀 Backend (Ganglia Core)

The backend is a Vert.x application that hosts the Agent Loop and the EventBus Bridge.

### Starting the Backend
1. **Build the project:**
   ```bash
   mvn clean install -DskipTests
   ```
2. **Run via dedicated WebUI Demo:**
   The `ganglia-example` module contains a `WebUIDemo` class designed for this.
   ```bash
   cd ganglia-example
   mvn exec:java -Dexec.mainClass="work.ganglia.example.WebUIDemo"
   ```
   *Note: The WebUI Verticle starts automatically on port **8080** during bootstrap.*

### Debugging the Backend
- **Logs:** Check `.ganglia/logs/` or the console output.
- **EventBus Monitoring:** The `WebUIEventPublisher` logs events sent to the UI.
- **API Endpoint:** The SockJS bridge is available at `http://localhost:8080/eventbus`. You can check `http://localhost:8080/eventbus/info` in your browser to verify the bridge is up.

---

## 💻 Frontend (Ganglia WebUI)

The frontend is a Vue 3 application built with Vite and Tailwind CSS.

### Development Mode
Use this for a fast feedback loop with Hot Module Replacement (HMR).
1. **Navigate to the UI directory:**
   ```bash
   cd ganglia-webui
   ```
2. **Install dependencies:**
   ```bash
   npm install
   ```
3. **Run the dev server:**
   ```bash
   npm run dev
   ```
   The UI will be available at `http://localhost:5173`. It is configured to connect to the backend at `http://localhost:8080` automatically in dev mode.

### Production Build
To serve the UI directly from the Java backend:
1. **Build the frontend:**
   ```bash
   cd ganglia-webui
   npm run build
   ```
2. **Deploy to backend:**
   Copy the contents of `ganglia-webui/dist/` to `ganglia-core/src/main/resources/webroot/`.
   ```bash
   mkdir -p ../ganglia-core/src/main/resources/webroot
   cp -r dist/* ../ganglia-core/src/main/resources/webroot/
   ```
3. Restart the backend. The UI will be available at `http://localhost:8080/index.html`.

---

## 🔌 EventBus Protocol

### Communication Addresses
- **`ganglia.ui.req`**: (Inbound) Front -> Back. For commands like `START`, `CANCEL`, `RESPOND_ASK`.
- **`ganglia.ui.stream.{sessionId}`**: (Outbound) Back -> Front. For state updates, thoughts, and tool results.
- **`ganglia.ui.stream.{sessionId}.tty`**: (Outbound Bypass) Back -> Front. High-frequency raw terminal output.

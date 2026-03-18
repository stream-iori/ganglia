# Ganglia Project Management Justfile

# Set directory variables
backend_dir  := "ganglia-core"
frontend_dir := "ganglia-webui"
example_dir  := "ganglia-example"

# List available commands
default:
    @just --list

# --- Setup & Installation ---

# Initialize the whole project (Java install + NPM install)
setup:
    mvn clean install -DskipTests
    cd {{frontend_dir}} && npm install

# --- Development ---

# Start the Backend (WebUI dedicated demo on 8080)
backend:
    cd {{example_dir}} && mvn exec:java -Dexec.mainClass="work.ganglia.example.WebUIDemo"

# Start the Frontend Dev Server (Vite on 5173) with HMR
frontend:
    cd {{frontend_dir}} && npm run dev

# Build the frontend in watch mode (continuously updates dist/)
# This allows WebUIDemo to serve the latest changes immediately.
ui-watch:
    cd {{frontend_dir}} && npm run build-watch

# Helper to start both for linked debugging
dev-all:
    @echo "To start linked development:"
    @echo "1. Run 'just ui-watch' in one terminal (keeps {{frontend_dir}}/dist updated)"
    @echo "2. Run 'just backend' in another terminal (serves assets from dist)"
    @echo "Alternatively, run 'just frontend' for Vite HMR on port 5173."

# --- Testing ---

# Run all tests (Backend & Frontend)
test: test-backend test-frontend

# Run Java backend unit tests
test-backend:
    mvn test -pl ganglia-core,ganglia-web,ganglia-terminal

# Run Java integration tests
test-it:
    mvn verify -pl integration-test

# Run a specific integration test class (e.g. just test-it-one AgentLoopIT)
test-it-one test_name:
    mvn verify -Dit.test={{test_name}} -pl integration-test

# Run Vue frontend tests
test-frontend:
    cd {{frontend_dir}} && npx vitest run

# --- Coverage ---

# Generate and display JaCoCo test coverage report
coverage: _run-coverage _print-coverage

_run-coverage:
    mvn test -pl ganglia-core,ganglia-web,ganglia-terminal

_print-coverage:
    python3 scripts/print_coverage.py

# --- Build & Deploy ---

# Build the frontend and sync its output to the Java backend webroot
build-ui:
    cd {{frontend_dir}} && npm run build
    mkdir -p {{backend_dir}}/src/main/resources/webroot
    cp -r {{frontend_dir}}/dist/* {{backend_dir}}/src/main/resources/webroot/
    @echo "UI synced to {{backend_dir}}/src/main/resources/webroot"

# Perform a full production build (UI -> Backend JAR)
build-all: build-ui
    mvn clean install -DskipTests

# --- Cleanup ---

# Clean all build artifacts
clean:
    mvn clean
    rm -rf {{frontend_dir}}/dist
    rm -rf {{frontend_dir}}/node_modules

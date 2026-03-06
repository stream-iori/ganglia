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

# Start the Frontend Dev Server (Vite on 5173)
frontend:
    cd {{frontend_dir}} && npm run dev

# --- Testing ---

# Run all tests (Backend & Frontend)
test: test-backend test-frontend

# Run Java backend tests
test-backend:
    mvn test

# Run Vue frontend tests
test-frontend:
    cd {{frontend_dir}} && npx vitest run

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

#!/bin/bash

# project-setup.sh - Setup development environment and Git hooks

# Exit on error
set -e

# Change to project root
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$PROJECT_ROOT"

# --- GIT HOOKS SETUP ---
echo "--- Setting up Git hooks ---"
# Set core.hooksPath to our custom hooks directory (.githooks)
git config core.hooksPath .githooks

# Ensure scripts are executable
chmod +x .githooks/*
chmod +x scripts/*.sh

echo "Git hooks successfully configured to use .githooks/"

# --- PROJECT DEPENDENCIES CHECK (Optional, for developer convenience) ---
echo "--- Checking project dependencies ---"

if command -v mvn &> /dev/null; then
  echo "[OK] Maven is installed."
else
  echo "[WARNING] Maven is NOT installed. You will need it for Java development."
fi

if command -v npm &> /dev/null; then
  echo "[OK] npm is installed."
else
  echo "[WARNING] npm is NOT installed. You will need it for Node.js development."
fi

if [ -d "/Users/stream/.sdkman/candidates/java/17-zulu" ]; then
  echo "[OK] Java 17 (Zulu) found at expected location."
else
  echo "[WARNING] Java 17 (Zulu) NOT found at expected location. Ensure JAVA_HOME is set correctly."
fi

echo "--- Project setup complete! ---"

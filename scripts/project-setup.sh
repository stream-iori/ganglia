#!/bin/bash

# project-setup.sh - Setup development environment and Git hooks

# Exit on error
set -e

# Change to project root
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
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

if command -v java &> /dev/null; then
  JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d '"' -f 2 | cut -d '.' -f 1)
  if [ "$JAVA_VERSION" = "17" ]; then
    echo "[OK] Java 17 is installed."
  else
    echo "[WARNING] Java version is not 17 (found $JAVA_VERSION). Ganglia requires Java 17."
  fi
else
  echo "[WARNING] Java is NOT installed. You will need Java 17 for development."
fi

echo "--- Project setup complete! ---"

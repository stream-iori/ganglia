#!/bin/bash

# check-style.sh - Style and Lint check for Java and Node.js

# Fail on error
set -e

# Change to project root (in case script is called from elsewhere)
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

# --- JAVA CHECKS (Spotless & Checkstyle) ---
echo "--- Running Java Style Checks (Spotless & Checkstyle) ---"
if command -v mvn &> /dev/null; then
  mvn spotless:check checkstyle:check
else
  echo "WARNING: Maven not found, skipping Java style checks."
fi

# --- NODE.JS CHECKS (ESLint & Prettier) ---
echo "--- Running Node.js Style Checks (ESLint & Prettier) ---"
if [ -d "ganglia-webui" ]; then
  cd ganglia-webui
  if command -v npm &> /dev/null; then
    # Ensure dependencies are installed (optional, but good for hook reliability)
    # npm install --no-fund --no-audit --silent 
    
    # Run linting (which includes prettier via eslint-plugin-prettier)
    npm run lint
    echo "Node.js checks passed."
  else
    echo "WARNING: npm not found, skipping Node.js style checks."
  fi
  cd "$PROJECT_ROOT"
else
  echo "INFO: No Node.js project found in ganglia-webui, skipping."
fi

echo "--- All style checks passed successfully! ---"

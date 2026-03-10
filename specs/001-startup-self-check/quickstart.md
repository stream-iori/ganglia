# Quickstart: Startup Self-Check & Config Initialization

## Introduction
The Startup Self-Check ensures that your environment is correctly set up for the Ganglia agent framework. If the `.ganglia/config.json` file is missing, the system will automatically initialize it with sensible defaults.

## First-Run Experience

1.  **Start the application**: Run the agent using `just backend` or a similar startup command.
2.  **Self-Check Execution**:
    -   The system identifies that `.ganglia/config.json` is missing.
    -   It creates the `.ganglia` directory if necessary.
    -   It initializes a new `config.json` with Standard LLM Configuration.
3.  **User Notification**: A message will appear in the console/logs:
    ```
    INFO [ConfigManager] No configuration file found at .ganglia/config.json. Initializing new configuration with defaults.
    ```
4.  **Action Required**: Open `.ganglia/config.json` and provide your `apiKey` and other specific settings.

## Manual Triggering
If you want to reset your configuration, simply delete the `.ganglia/config.json` file and restart the application.

## Troubleshooting
If initialization fails (e.g., due to read-only permissions), the system will output a clear error message:
```
ERROR [ConfigManager] Critical error: Unable to create configuration file. Permission denied.
```
Ensure the application has write permissions to the working directory.

# ReAct Loop Implementation Details

This document details the internal mechanics of the **ReAct (Reason + Act)** Loop, the core execution pattern for the Caprice framework.

## 1. The Concept

The ReAct Loop is a state machine that cycles through three states until a termination condition is met:

1.  **Thought:** The agent reasons about the current state and decides what to do next.
2.  **Action:** The agent emits a command to use a tool.
3.  **Observation:** The tool executes and returns a result to the agent context.

## 2. The Core Loop Logic (Python Pseudo-code)

This pseudo-code illustrates the `execute` method of a ReAct Agent.

```python
class ReActAgent:
    def __init__(self, llm, tools):
        self.llm = llm
        self.tools = {t.name: t for t in tools}
        self.max_steps = 10

    def execute(self, user_goal):
        # 1. Initialize Context
        # The system prompt instructs the LLM on HOW to behave (Thought -> Action format)
        system_prompt = self._construct_system_prompt()

        # History tracks the conversation: User Goal -> Thoughts -> Actions -> Observations
        history = [("user", user_goal)]

        steps_taken = 0

        while steps_taken < self.max_steps:
            # 2. Call LLM
            # Flatten history into a prompt string or chat messages
            response = self.llm.chat(system_prompt, history)

            # 3. Parse Output
            # We expect the LLM to output one of two patterns:
            # Pattern A: Thought + Action (I need to check weather -> Weather(city='London'))
            # Pattern B: Final Answer (The weather is sunny.)
            parsed_output = self._parse_llm_output(response)

            if parsed_output.is_final_answer():
                return parsed_output.answer

            # 4. Execute Tool (Action)
            tool_name = parsed_output.action.tool_name
            tool_args = parsed_output.action.args

            print(f"Agent thought: {parsed_output.thought}")
            print(f"Invoking tool: {tool_name} with {tool_args}")

            try:
                tool_result = self.tools[tool_name].run(**tool_args)
            except Exception as e:
                tool_result = f"Error executing tool: {e}"

            # 5. Update Context (Observation)
            # Crucial: The result must be fed back to the LLM so it knows what happened.
            history.append(("assistant", response)) # The agent's thought/action request
            history.append(("tool_output", tool_result)) # The observation

            steps_taken += 1

        return "Task failed: Max steps reached without final answer."

    def _construct_system_prompt(self):
        return """
        You are a helpful assistant. Solve the user's request using the following tools:
        {tool_descriptions}

        Use the following format:

        Question: the input question you must answer
        Thought: you should always think about what to do
        Action: the action to take, should be one of [{tool_names}]
        Action Input: the input to the action
        Observation: the result of the action
        ... (this Thought/Action/Action Input/Observation can repeat N times)
        Thought: I now know the final answer
        Final Answer: the final answer to the original input question
        """
```

## 3. Key Implementation Challenges for Java

### A. Parsing Strategy

LLMs output text. We need robust REGEX or structured output (JSON mode) to separate the `Thought` from the `Action`.

- **Text Parsing:** Use Regex to find `Action: (\w+)` and `Action Input: (.*)`. Fragile if the LLM hallucinates formatting.
- **Tool Calling API (OpenAI/Gemini):** Modern LLMs support "Function Calling" natively. The "Action" step is replaced by the API returning a `tool_calls` object, and "Observation" is a `tool_role` message. **Caprice should prefer this native mode where available.**

### B. Context Management

The `history` list grows rapidly.

- **Token Limits:** We need a `ContextManager` that summarizes or prunes old `Thought/Observation` pairs if the prompt gets too long.
- **Observation Formatting:** Large tool outputs (e.g., a 1MB JSON file) must be truncated or summarized before being added to the history.

### C. Exception Handling

What if the tool fails?

- The exception message (e.g., "404 Not Found") should be treated as an `Observation`.
- The Agent sees this error in the next turn and can decide to `Thought: "The URL was wrong, I will try a search instead."` -> `Action: Search(...)`.

---

id: it-test-skill
name: IT Test Skill
description: A skill for integration testing
filePatterns: ["*.it-test"]
tools:
- name: it_test_tool
description: A dummy tool for testing
type: SCRIPT
script:
command: "echo 'IT_TOOL_OUTPUT: {{message}}'"
schema: |
{
"type": "object",
"properties": {
"message": { "type": "string" }
}
}
---

# IT Test Instructions

When this skill is active, you must always start your responses with "SKILL_ACTIVE:".
If asked to use the specialized IT tool, call 'it_test_tool'.

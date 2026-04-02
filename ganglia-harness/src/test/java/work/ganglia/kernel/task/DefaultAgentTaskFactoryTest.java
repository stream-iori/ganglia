package work.ganglia.kernel.task;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolExecutor;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.internal.skill.SkillRuntime;
import work.ganglia.port.internal.skill.SkillService;

class DefaultAgentTaskFactoryTest {

  private static SessionContext emptyContext() {
    return new SessionContext(
        "sid",
        Collections.emptyList(),
        null,
        Collections.emptyMap(),
        Collections.emptyList(),
        null,
        null);
  }

  private static ToolExecutor mockExecutor() {
    ToolExecutor exec = mock(ToolExecutor.class);
    when(exec.getAvailableTools(any())).thenReturn(Collections.emptyList());
    return exec;
  }

  // -------------------------------------------------------------------------
  // create() routing
  // -------------------------------------------------------------------------

  @Test
  void create_callSubAgent_returnsSubAgentTask() {
    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(mock(AgentLoopFactory.class), mockExecutor(), null, null, null);

    ToolCall call = new ToolCall("id1", "call_sub_agent", Map.of("task", "do it"));
    AgentTask task = factory.create(call, emptyContext());

    assertInstanceOf(SubAgentTask.class, task);
  }

  @Test
  void create_standardTool_returnsStandardToolTask() {
    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(mock(AgentLoopFactory.class), mockExecutor(), null, null, null);

    ToolCall call = new ToolCall("id2", "read_file", Map.of());
    AgentTask task = factory.create(call, emptyContext());

    assertInstanceOf(StandardToolTask.class, task);
  }

  @Test
  void create_listAvailableSkills_returnsSkillTask() {
    SkillService skillService = mock(SkillService.class);
    SkillRuntime skillRuntime = mock(SkillRuntime.class);
    when(skillRuntime.getActiveSkillsTools(any())).thenReturn(Collections.emptyList());

    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(
            mock(AgentLoopFactory.class), mockExecutor(), null, skillService, skillRuntime);

    ToolCall call = new ToolCall("id3", "list_available_skills", Map.of());
    AgentTask task = factory.create(call, emptyContext());

    assertInstanceOf(SkillTask.class, task);
  }

  @Test
  void create_activateSkill_returnsSkillTask() {
    SkillService skillService = mock(SkillService.class);
    SkillRuntime skillRuntime = mock(SkillRuntime.class);
    when(skillRuntime.getActiveSkillsTools(any())).thenReturn(Collections.emptyList());

    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(
            mock(AgentLoopFactory.class), mockExecutor(), null, skillService, skillRuntime);

    ToolCall call = new ToolCall("id4", "activate_skill", Map.of("skillId", "s1"));
    AgentTask task = factory.create(call, emptyContext());

    assertInstanceOf(SkillTask.class, task);
  }

  @Test
  void create_activeSkillTool_returnsSkillTask() {
    SkillService skillService = mock(SkillService.class);
    SkillRuntime skillRuntime = mock(SkillRuntime.class);

    ToolDefinition toolDef = new ToolDefinition("custom_skill_tool", "desc", "{}");
    ToolSet toolSet = mock(ToolSet.class);
    when(toolSet.getDefinitions()).thenReturn(List.of(toolDef));
    when(skillRuntime.getActiveSkillsTools(any())).thenReturn(List.of(toolSet));

    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(
            mock(AgentLoopFactory.class), mockExecutor(), null, skillService, skillRuntime);

    ToolCall call = new ToolCall("id5", "custom_skill_tool", Map.of());
    AgentTask task = factory.create(call, emptyContext());

    assertInstanceOf(SkillTask.class, task);
  }

  @Test
  void create_noSkillService_skillToolNameFallsToStandard() {
    // skillService/skillRuntime are null, so isSkillTool returns false
    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(mock(AgentLoopFactory.class), mockExecutor(), null, null, null);

    ToolCall call = new ToolCall("id6", "list_available_skills", Map.of());
    AgentTask task = factory.create(call, emptyContext());

    assertInstanceOf(StandardToolTask.class, task);
  }

  // -------------------------------------------------------------------------
  // getAvailableDefinitions()
  // -------------------------------------------------------------------------

  @Test
  void getAvailableDefinitions_withSkillRuntime_includesSkillManagementTools() {
    SkillService skillService = mock(SkillService.class);
    SkillRuntime skillRuntime = mock(SkillRuntime.class);
    when(skillRuntime.getActiveSkillsTools(any())).thenReturn(Collections.emptyList());

    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(
            mock(AgentLoopFactory.class), mockExecutor(), null, skillService, skillRuntime);

    List<ToolDefinition> defs = factory.getAvailableDefinitions(emptyContext());

    assertTrue(defs.stream().anyMatch(d -> "list_available_skills".equals(d.name())));
    assertTrue(defs.stream().anyMatch(d -> "activate_skill".equals(d.name())));
  }

  @Test
  void getAvailableDefinitions_withActiveSkillTools_includesTheirDefinitions() {
    SkillService skillService = mock(SkillService.class);
    SkillRuntime skillRuntime = mock(SkillRuntime.class);

    ToolDefinition skillTool = new ToolDefinition("my_skill_tool", "desc", "{}");
    ToolSet toolSet = mock(ToolSet.class);
    when(toolSet.getDefinitions()).thenReturn(List.of(skillTool));
    when(skillRuntime.getActiveSkillsTools(any())).thenReturn(List.of(toolSet));

    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(
            mock(AgentLoopFactory.class), mockExecutor(), null, skillService, skillRuntime);

    List<ToolDefinition> defs = factory.getAvailableDefinitions(emptyContext());

    assertTrue(defs.stream().anyMatch(d -> "my_skill_tool".equals(d.name())));
  }

  @Test
  void getAvailableDefinitions_alwaysIncludesCallSubAgent() {
    DefaultAgentTaskFactory factory =
        new DefaultAgentTaskFactory(mock(AgentLoopFactory.class), mockExecutor(), null, null, null);

    List<ToolDefinition> defs = factory.getAvailableDefinitions(emptyContext());

    assertTrue(defs.stream().anyMatch(d -> "call_sub_agent".equals(d.name())));
  }
}

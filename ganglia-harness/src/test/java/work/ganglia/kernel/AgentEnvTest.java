package work.ganglia.kernel;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;

import work.ganglia.kernel.task.AgentTask;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.external.tool.ToolDefinition;

@ExtendWith(VertxExtension.class)
class AgentEnvTest {

  @Test
  void testBuilderAndGetters(Vertx vertx) {
    AgentEnv env =
        AgentEnv.builder()
            .vertx(vertx)
            .modelGateway(null)
            .sessionManager(null)
            .promptEngine(null)
            .configProvider(null)
            .modelConfig(null)
            .compressor(null)
            .memoryService(null)
            .dispatcher(null)
            .faultTolerancePolicy(null)
            .contextOptimizer(null)
            .taskFactory(null)
            .build();

    assertSame(vertx, env.vertx());
    assertNull(env.modelGateway());
    assertNull(env.sessionManager());
    assertNull(env.promptEngine());
    assertNull(env.configProvider());
    assertNull(env.modelConfig());
    assertNull(env.compressor());
    assertNull(env.memoryService());
    assertNull(env.dispatcher());
    assertNull(env.faultTolerancePolicy());
    assertNull(env.contextOptimizer());
    assertNull(env.taskFactory());
  }

  @Test
  void testSetTaskFactory(Vertx vertx) {
    AgentEnv env = AgentEnv.builder().vertx(vertx).build();
    assertNull(env.taskFactory());

    AgentTaskFactory factory =
        new AgentTaskFactory() {
          @Override
          public AgentTask create(ToolCall call, SessionContext context) {
            return null;
          }

          @Override
          public List<ToolDefinition> getAvailableDefinitions(SessionContext context) {
            return List.of();
          }
        };
    env.setTaskFactory(factory);
    assertSame(factory, env.taskFactory());
  }

  @Test
  void testBuilderReturnsNewInstance(Vertx vertx) {
    AgentEnv env1 = AgentEnv.builder().vertx(vertx).build();
    AgentEnv env2 = AgentEnv.builder().vertx(vertx).build();
    assertNotSame(env1, env2);
  }
}

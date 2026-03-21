package work.ganglia.kernel.loop;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import work.ganglia.infrastructure.internal.memory.DefaultContextCompressor;
import work.ganglia.infrastructure.internal.memory.TokenCounter;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
import work.ganglia.infrastructure.internal.state.DefaultSessionManager;
import work.ganglia.kernel.AgentEnv;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelResponse;
import work.ganglia.port.external.tool.ToolCall;
import work.ganglia.port.internal.state.AgentSignal;
import work.ganglia.port.internal.state.SessionManager;
import work.ganglia.stubs.*;

@ExtendWith(VertxExtension.class)
public class ReActAgentLoopTest {

  private Vertx vertx;
  private StubModelGateway model;
  private SessionManager sessionManager;
  private StubPromptEngine prompt;
  private StubConfigManager configManager;
  private ReActAgentLoop loop;
  private AgentEnv env;
  private StubToolExecutor tools;

  @BeforeEach
  void setUp(Vertx vertx) {
    this.vertx = vertx;
    model = new StubModelGateway();
    configManager = new StubConfigManager(vertx);
    sessionManager =
        new DefaultSessionManager(
            new InMemoryStateEngine(), new InMemoryLogManager(), configManager);
    prompt = new StubPromptEngine();
    DefaultContextCompressor compressor = new DefaultContextCompressor(model, configManager);
    tools = new StubToolExecutor();
    DefaultContextOptimizer optimizer =
        new DefaultContextOptimizer(configManager, configManager, compressor, new TokenCounter());

    AgentLoopFactory loopFactory = () -> loop; // cyclic dependency hack for tests
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(loopFactory, tools, null, null, null);

    loop =
        ReActAgentLoop.builder()
            .vertx(vertx)
            .dispatcher(new DefaultObservationDispatcher(vertx))
            .sessionManager(sessionManager)
            .configProvider(configManager)
            .contextOptimizer(optimizer)
            .promptEngine(prompt)
            .modelGateway(model)
            .taskFactory(taskFactory)
            .faultTolerancePolicy(new ConsecutiveFailurePolicy())
            .build();
  }

  @Test
  void testSuccessfulDirectAnswer(VertxTestContext testContext) {
    String answer = "The capital of France is Paris.";
    model.addResponse(new ModelResponse(answer, Collections.emptyList(), null));

    SessionContext context = sessionManager.createSession(UUID.randomUUID().toString());

    loop.run("What is the capital of France?", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("Paris"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testToolCallAndRecursion(VertxTestContext testContext) {
    // Turn 1: Model calls a tool
    ToolCall call = new ToolCall("c1", "test_tool", Map.of("arg", "val"));
    model.addResponse(new ModelResponse("I need to use a tool.", List.of(call), null));

    // Turn 2: Model gives final answer after tool result
    model.addResponse(new ModelResponse("The tool said OK.", Collections.emptyList(), null));

    SessionContext context = sessionManager.createSession(UUID.randomUUID().toString());

    loop.run("Use the tool.", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result -> {
                  testContext.verify(
                      () -> {
                        assertTrue(result.contains("said OK"));
                        testContext.completeNow();
                      });
                }));
  }

  @Test
  void testAgentInterruptAndResume(VertxTestContext testContext) {
    // Turn 1: Model calls a tool that requires user input
    ToolCall call = new ToolCall("c1", "ask_user", Map.of("question", "Do you approve?"));
    model.addResponse(new ModelResponse("I need your approval.", List.of(call), null));

    // Turn 2: Model gives final answer after tool result
    model.addResponse(new ModelResponse("Approved. Proceeding.", Collections.emptyList(), null));

    tools.registerHandler(
        "ask_user",
        tc ->
            work.ganglia.infrastructure.external.tool.model.ToolInvokeResult.interrupt(
                "Pause for input"));

    SessionContext context = sessionManager.createSession(UUID.randomUUID().toString());

    loop.run("Do task", context, new AgentSignal())
        .onComplete(
            testContext.succeeding(
                result1 -> {
                  testContext.verify(() -> assertTrue(result1.contains("Pause for input")));

                  // Fetch the updated context from memory to simulate a fresh resume request
                  sessionManager
                      .getSession(context.sessionId())
                      .onComplete(
                          testContext.succeeding(
                              updatedContext -> {
                                loop.resume("Yes, I approve", updatedContext, new AgentSignal())
                                    .onComplete(
                                        testContext.succeeding(
                                            result2 -> {
                                              testContext.verify(
                                                  () -> {
                                                    assertTrue(
                                                        result2.contains("Approved. Proceeding."));
                                                    testContext.completeNow();
                                                  });
                                            }));
                              }));
                }));
  }

  @Test
  void testAgentStop(VertxTestContext testContext) {
    SessionContext context = sessionManager.createSession(UUID.randomUUID().toString());
    AgentSignal signal = new AgentSignal();

    // Abort the signal before running
    signal.abort();

    loop.run("Do task", context, signal)
        .onComplete(
            testContext.failing(
                err -> {
                  testContext.verify(
                      () -> {
                        assertTrue(err instanceof AgentAbortedException);
                        testContext.completeNow();
                      });
                }));
  }
}

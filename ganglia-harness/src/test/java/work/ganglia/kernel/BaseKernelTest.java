package work.ganglia.kernel;

import org.junit.jupiter.api.BeforeEach;

import io.vertx.core.Vertx;

import work.ganglia.BaseGangliaTest;
import work.ganglia.infrastructure.internal.state.DefaultContextOptimizer;
import work.ganglia.kernel.loop.AgentLoopFactory;
import work.ganglia.kernel.loop.ConsecutiveFailurePolicy;
import work.ganglia.kernel.loop.DefaultObservationDispatcher;
import work.ganglia.kernel.loop.ReActAgentLoop;
import work.ganglia.kernel.task.AgentTaskFactory;
import work.ganglia.kernel.task.DefaultAgentTaskFactory;
import work.ganglia.port.internal.memory.ContextCompressor;
import work.ganglia.stubs.StubContextCompressor;
import work.ganglia.stubs.StubModelGateway;
import work.ganglia.stubs.StubPromptEngine;
import work.ganglia.stubs.StubToolExecutor;
import work.ganglia.util.TokenCounter;

/** Base class for testing kernel logic such as loops and interceptors. */
public abstract class BaseKernelTest extends BaseGangliaTest {
  protected StubModelGateway model;
  protected StubPromptEngine prompt;
  protected StubToolExecutor tools;
  protected ReActAgentLoop loop;
  protected ContextCompressor compressor;
  protected DefaultContextOptimizer optimizer;

  @BeforeEach
  protected void setUpKernel(Vertx vertx) {
    this.model = new StubModelGateway();
    this.prompt = new StubPromptEngine();
    this.tools = new StubToolExecutor();
    this.compressor = new StubContextCompressor();
    this.optimizer =
        new DefaultContextOptimizer(configManager, configManager, compressor, new TokenCounter());

    AgentLoopFactory loopFactory = () -> loop; // cyclic dependency hack for tests
    AgentTaskFactory taskFactory =
        new DefaultAgentTaskFactory(loopFactory, tools, null, null, null);

    this.loop =
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
}

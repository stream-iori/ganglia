package work.ganglia.trading.filter;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.vertx.core.Future;
import io.vertx.junit5.VertxTestContext;

import work.ganglia.BaseGangliaTest;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.tool.ToolDefinition;
import work.ganglia.port.external.tool.ToolSet;
import work.ganglia.port.external.tool.model.ToolInvokeResult;
import work.ganglia.port.internal.state.ExecutionContext;
import work.ganglia.stubs.StubExecutionContext;

class RoleAwareToolFilterTest extends BaseGangliaTest {

  private ToolSet stubToolSet;

  @BeforeEach
  void setUp() {
    stubToolSet =
        new ToolSet() {
          @Override
          public List<ToolDefinition> getDefinitions() {
            return List.of(new ToolDefinition("test_tool", "A test tool", "{}"));
          }

          @Override
          public Future<ToolInvokeResult> execute(
              String name, Map<String, Object> args, SessionContext ctx, ExecutionContext execCtx) {
            return Future.succeededFuture(ToolInvokeResult.success("stub output"));
          }
        };
  }

  @Nested
  class Availability {

    @Test
    void isAvailable_whenPersonaMatches() {
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "MARKET_ANALYST");
      RoleAwareToolFilter filter =
          new RoleAwareToolFilter(stubToolSet, Set.of("MARKET_ANALYST", "NEWS_ANALYST"));

      assertTrue(filter.isAvailableFor(context));
    }

    @Test
    void isNotAvailable_whenPersonaMismatch() {
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "TRADER");
      RoleAwareToolFilter filter = new RoleAwareToolFilter(stubToolSet, Set.of("MARKET_ANALYST"));

      assertFalse(filter.isAvailableFor(context));
    }

    @Test
    void isNotAvailable_whenNoPersonaMetadata() {
      SessionContext context = createSessionContext();
      RoleAwareToolFilter filter = new RoleAwareToolFilter(stubToolSet, Set.of("MARKET_ANALYST"));

      assertFalse(filter.isAvailableFor(context));
    }

    @Test
    void isNotAvailable_whenAllowedSetIsEmpty() {
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "MARKET_ANALYST");
      RoleAwareToolFilter filter = new RoleAwareToolFilter(stubToolSet, Set.of());

      assertFalse(filter.isAvailableFor(context));
    }
  }

  @Nested
  class ExecutionDelegation {

    @Test
    void delegatesToWrapped_onExecute(VertxTestContext testContext) {
      RoleAwareToolFilter filter = new RoleAwareToolFilter(stubToolSet, Set.of("MARKET_ANALYST"));
      SessionContext context =
          createSessionContext().withNewMetadata("sub_agent_persona", "MARKET_ANALYST");
      ExecutionContext execCtx = new StubExecutionContext();

      Future<ToolInvokeResult> result = filter.execute("test_tool", Map.of(), context, execCtx);

      assertFutureSuccess(
          result,
          testContext,
          r -> {
            assertEquals(ToolInvokeResult.Status.SUCCESS, r.status());
            assertEquals("stub output", r.output());
          });
    }

    @Test
    void returnsWrappedDefinitions() {
      RoleAwareToolFilter filter = new RoleAwareToolFilter(stubToolSet, Set.of("MARKET_ANALYST"));

      List<ToolDefinition> definitions = filter.getDefinitions();

      assertEquals(1, definitions.size());
      assertEquals("test_tool", definitions.get(0).name());
      assertEquals("A test tool", definitions.get(0).description());
    }
  }
}

package work.ganglia.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import io.vertx.core.Vertx;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import work.ganglia.BootstrapOptions;
import work.ganglia.coding.CodingAgentBuilder;
import work.ganglia.port.chat.SessionContext;
import work.ganglia.port.external.llm.ModelGateway;

public class PromptAndSkillIT extends MockModelIT {

  @Test
  void systemPrompt_containsCoreContextFragments(Vertx vertx, VertxTestContext testContext) {
    SessionContext context = newSession();

    ganglia
        .env()
        .promptEngine()
        .buildSystemPrompt(context)
        .onComplete(
            testContext.succeeding(
                (String prompt) ->
                    testContext.verify(
                        () -> {
                          assertNotNull(prompt);
                          String upperPrompt = prompt.toUpperCase();
                          assertTrue(
                              upperPrompt.contains("PERSONA"), "Prompt should contain Persona");
                          assertTrue(
                              upperPrompt.contains("OS"), "Prompt should contain OS information");
                          assertTrue(
                              upperPrompt.contains("WORKING DIRECTORY"),
                              "Prompt should contain Environment");
                          testContext.completeNow();
                        })));
  }

  @Test
  void customInstructionFile_appearsInPrompt(Vertx vertx, VertxTestContext testContext) {
    String customFile = "AGENTS.md";
    ModelGateway inlineMock = mock(ModelGateway.class);

    BootstrapOptions options = BootstrapOptions.builder().modelGatewayOverride(inlineMock).build();

    CodingAgentBuilder.create(vertx)
        .withOptions(options)
        .withInstructionFile(customFile)
        .bootstrap()
        .compose(
            g -> {
              SessionContext context =
                  g.sessionManager().createSession(UUID.randomUUID().toString());
              return g.env().promptEngine().buildSystemPrompt(context);
            })
        .onComplete(
            testContext.succeeding(
                prompt ->
                    testContext.verify(
                        () -> {
                          assertTrue(
                              prompt.contains(customFile),
                              "Prompt should contain the custom instruction filename: "
                                  + customFile);
                          testContext.completeNow();
                        })));
  }

  @Test
  void skillSystem_injectsPersonaIntoPrompt(Vertx vertx, VertxTestContext testContext) {
    SessionContext context = newSession();

    ganglia
        .env()
        .promptEngine()
        .buildSystemPrompt(context)
        .onComplete(
            testContext.succeeding(
                (String prompt) ->
                    testContext.verify(
                        () -> {
                          assertNotNull(prompt);
                          assertTrue(
                              prompt.toUpperCase().contains("PERSONA"),
                              "Prompt should contain Persona section");
                          testContext.completeNow();
                        })));
  }

  @Test
  void dynamicSkillLoading_bootstrapsSuccessfully(Vertx vertx, VertxTestContext testContext) {
    assertNotNull(ganglia.agentLoop());
    testContext.completeNow();
  }
}

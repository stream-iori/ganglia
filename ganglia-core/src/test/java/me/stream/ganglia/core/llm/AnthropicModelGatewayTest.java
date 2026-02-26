package me.stream.ganglia.core.llm;

import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.http.AsyncStreamResponse;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.*;
import com.anthropic.services.async.MessageServiceAsync;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import me.stream.ganglia.core.model.ModelOptions;
import me.stream.ganglia.core.model.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class AnthropicModelGatewayTest {

    @Mock
    private AnthropicClientAsync client;

    @Mock
    private MessageServiceAsync messageService;

    @Mock
    private EventBus eventBus;

    @Mock
    private MessageAccumulator accumulator;

    private AnthropicModelGateway gateway;

    private Vertx vertx;

    @BeforeEach
    void setUp(Vertx vertx) {
        this.vertx = spy(vertx);
        when(client.messages()).thenReturn(messageService);
        gateway = new AnthropicModelGateway(this.vertx, client, () -> accumulator);
    }

    @Test
    void testChatMapping(VertxTestContext testContext) {
        List<Message> history = List.of(Message.user("Hello"));
        ModelOptions options = new ModelOptions(0.0, 1024, "claude-3-5-sonnet-20241022");

        com.anthropic.models.messages.Message anthropicMessage = mock(com.anthropic.models.messages.Message.class);
        when(anthropicMessage.content()).thenReturn(List.of(
            ContentBlock.ofText(TextBlock.builder().text("Hi there!").citations(Collections.emptyList()).build())
        ));

        com.anthropic.models.messages.Usage usage = mock(com.anthropic.models.messages.Usage.class);
        when(usage.inputTokens()).thenReturn(10L);
        when(usage.outputTokens()).thenReturn(5L);
        when(anthropicMessage.usage()).thenReturn(usage);

        when(messageService.create(any(MessageCreateParams.class)))
            .thenReturn(CompletableFuture.completedFuture(anthropicMessage));

        gateway.chat(history, Collections.emptyList(), options)
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals("Hi there!", response.content());
                    assertEquals(10, response.usage().promptTokens());
                    assertEquals(5, response.usage().completionTokens());
                    testContext.completeNow();
                });
            }));
    }

    @Test
    void testChatStreamMapping(VertxTestContext testContext) {
        List<Message> history = List.of(Message.user("Stream this"));
        ModelOptions options = new ModelOptions(0.0, 1024, "claude-3-5-sonnet-20241022");
        String sessionId = "test-session";

        when(vertx.eventBus()).thenReturn(eventBus);

        var streamResponse = mock(AsyncStreamResponse.class);
        when(messageService.createStreaming(any())).thenReturn(streamResponse);

        // Mock accumulator.message() for final response
        var finalAnthropicMessage = mock(com.anthropic.models.messages.Message.class);
        when(finalAnthropicMessage.content()).thenReturn(List.of(
            ContentBlock.ofText(TextBlock.builder().text("Streamed content").citations(Collections.emptyList()).build())
        ));
        com.anthropic.models.messages.Usage usage = mock(com.anthropic.models.messages.Usage.class);
        when(usage.inputTokens()).thenReturn(10L);
        when(usage.outputTokens()).thenReturn(5L);
        when(finalAnthropicMessage.usage()).thenReturn(usage);
        when(accumulator.message()).thenReturn(finalAnthropicMessage);

        ArgumentCaptor<com.anthropic.core.http.AsyncStreamResponse.Handler<RawMessageStreamEvent>> handlerCaptor = ArgumentCaptor.forClass(com.anthropic.core.http.AsyncStreamResponse.Handler.class);

        gateway.chatStream(history, Collections.emptyList(), options, sessionId)
            .onComplete(testContext.succeeding(response -> {
                testContext.verify(() -> {
                    assertEquals("Streamed content", response.content());
                    testContext.completeNow();
                });
            }));

        verify(streamResponse).subscribe(handlerCaptor.capture());
        com.anthropic.core.http.AsyncStreamResponse.Handler<RawMessageStreamEvent> handler = handlerCaptor.getValue();

        // Send Text Delta Event
        TextDelta textDelta = TextDelta.builder().text("Streamed content").build();
        RawContentBlockDelta delta = RawContentBlockDelta.ofText(textDelta);
        RawContentBlockDeltaEvent contentDeltaEvent = RawContentBlockDeltaEvent.builder()
                .index(0)
                .delta(delta)
                .build();
        var deltaStreamEvent = RawMessageStreamEvent.ofContentBlockDelta(contentDeltaEvent);

        handler.onNext(deltaStreamEvent);

        // Verify EventBus
        verify(eventBus).publish(eq("ganglia.observations." + sessionId), any(JsonObject.class));

        // Complete
        handler.onComplete(Optional.empty());
    }
}

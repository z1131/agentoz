package com.deepknow.agentoz.manager;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.ReasoningChunkEvent;
import io.agentscope.core.hook.ActingChunkEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.function.Consumer;

public class StreamingHook implements Hook {

    private final Consumer<StreamEvent> eventConsumer;

    public StreamingHook(Consumer<StreamEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof ReasoningChunkEvent chunkEvent) {
            Msg chunk = chunkEvent.getIncrementalChunk();
            if (chunk != null) {
                String text = chunk.getTextContent();
                if (text != null && !text.isEmpty()) {
                    eventConsumer.accept(new StreamEvent(StreamEventType.TEXT, text, null));
                }
            }
        } else if (event instanceof PreActingEvent actingEvent) {
            String toolName = actingEvent.getToolUse().getName();
            String toolInput = actingEvent.getToolUse().getInput().toString();
            eventConsumer.accept(new StreamEvent(StreamEventType.TOOL_CALL, toolName, toolInput));
        } else if (event instanceof PostActingEvent postActingEvent) {
            eventConsumer.accept(new StreamEvent(StreamEventType.TOOL_RESULT, null, null));
        }
        return Mono.just(event);
    }

    @Override
    public int priority() {
        return 50;
    }

    public enum StreamEventType {
        TEXT,
        THINKING,
        TOOL_CALL,
        TOOL_RESULT
    }

    public record StreamEvent(
            StreamEventType type,
            String content,
            String extra
    ) {}
}

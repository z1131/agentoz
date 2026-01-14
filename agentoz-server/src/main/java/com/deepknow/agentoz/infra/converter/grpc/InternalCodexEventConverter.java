package com.deepknow.agentoz.infra.converter.grpc;

import com.deepknow.agentoz.dto.InternalCodexEvent;
import codex.agent.RunTaskResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Proto → InternalCodexEvent 转换器
 *
 * <h3>🎯 职责</h3>
 * <p>将 Codex-Agent 的 Proto 响应转换为内部事件，完整保留原始 JSON</p>
 */
@Slf4j
public class InternalCodexEventConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 转换 RunTaskResponse (Proto) → InternalCodexEvent (内部 DTO)
     */
    public static InternalCodexEvent toInternalEvent(RunTaskResponse protoResponse) {
        if (protoResponse == null) {
            return null;
        }

        RunTaskResponse.EventCase eventCase = protoResponse.getEventCase();

        return switch (eventCase) {
            case CODEX_EVENT_JSON -> {
                String eventJson = protoResponse.getCodexEventJson();
                String eventType = extractEventType(eventJson);
                log.info("CODEX_EVENT_JSON: type={}, json={}", eventType, 
                    eventJson.length() > 200 ? eventJson.substring(0, 200) + "..." : eventJson);
                yield InternalCodexEvent.processing(eventType, eventJson);
            }
            case ADAPTER_LOG -> {
                String logMsg = protoResponse.getAdapterLog();
                log.info("ADAPTER_LOG: {}", logMsg);
                yield InternalCodexEvent.log(logMsg);
            }
            case ERROR -> {
                log.error("Codex error: {}", protoResponse.getError());
                yield InternalCodexEvent.error(protoResponse.getError());
            }
            case UPDATED_ROLLOUT -> {
                byte[] rollout = protoResponse.getUpdatedRollout().toByteArray();
                log.info("Received updated rollout: {} bytes", rollout.length);
                yield InternalCodexEvent.finished(rollout);
            }
            case EVENT_NOT_SET -> {
                log.warn("Received response with no event set");
                yield null;
            }
        };
    }

    /**
     * 从 JSON 中提取事件类型
     */
    private static String extractEventType(String eventJson) {
        if (eventJson == null || eventJson.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(eventJson);
            if (node.has("type")) {
                return node.get("type").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to extract event type from JSON: {}", e.getMessage());
        }
        return null;
    }
}

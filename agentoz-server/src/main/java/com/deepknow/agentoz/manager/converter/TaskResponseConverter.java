package com.deepknow.agentoz.manager.converter;

import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * InternalCodexEvent → TaskResponse 转换器
 *
 * <h3>🎯 职责</h3>
 * <p>将内部事件转换为 API 层 DTO，实现内部协议与对外契约的解耦</p>
 *
 * <h3>📦 转换策略（优化版）</h3>
 * <ul>
 *   <li>✅ 直接透传：将 Codex 原始事件 JSON 直接放入 rawCodexEvents 字段</li>
 *   <li>⚠️ 保留兼容：为保持向后兼容，仍填充旧字段（标记为 @Deprecated）</li>
 * </ul>
 *
 * <h3>🔄 事件类型映射</h3>
 * <ul>
 *   <li>agent_message_delta → rawCodexEvents + textDelta (兼容)</li>
 *   <li>agent_reasoning_delta → rawCodexEvents + reasoningDelta (兼容)</li>
 *   <li>agent_message → rawCodexEvents + finalResponse (兼容)</li>
 *   <li>item_completed → rawCodexEvents + newItemsJson (兼容)</li>
 *   <li>token_count → usage</li>
 *   <li>updated_rollout → updatedRollout</li>
 *   <li>error → errorMessage</li>
 * </ul>
 */
@Slf4j
public class TaskResponseConverter {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 转换 InternalCodexEvent → TaskResponse
     *
     * <p>优化策略：直接透传 Codex 原始事件，同时保留旧字段以兼容现有代码</p>
     */
    public static TaskResponse toTaskResponse(InternalCodexEvent event) {
        if (event == null) {
            return null;
        }

        TaskResponse dto = new TaskResponse();

        // 设置状态
        dto.setStatus(event.getStatus().name());

        // 根据状态处理
        switch (event.getStatus()) {
            case ERROR -> {
                dto.setErrorMessage(event.getErrorMessage());
            }
            case FINISHED -> {
                dto.setUpdatedRollout(event.getUpdatedRollout());
            }
            case PROCESSING -> {
                // ✅ 核心：直接透传 Codex 原始事件
                if (event.getRawEventJson() != null) {
                    dto.setRawCodexEvents(List.of(event.getRawEventJson()));
                }

                // ⚠️ 兼容旧代码：继续填充旧字段（逐步废弃）
                parseEventToResponse(event, dto);
            }
        }

        return dto;
    }

    /**
     * 解析 Codex 事件并填充到 TaskResponse
     */
    private static void parseEventToResponse(InternalCodexEvent event, TaskResponse dto) {
        String eventType = event.getEventType();
        String rawJson = event.getRawEventJson();

        if (eventType == null || rawJson == null) {
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(rawJson);

            switch (eventType) {
                // 文本增量
                case "agent_message_delta" -> {
                    if (node.has("delta") && node.get("delta").has("text")) {
                        dto.setTextDelta(node.get("delta").get("text").asText());
                    }
                }

                // 推理增量
                case "agent_reasoning_delta" -> {
                    if (node.has("delta") && node.get("delta").has("text")) {
                        dto.setReasoningDelta(node.get("delta").get("text").asText());
                    }
                }

                // 完整消息
                case "agent_message" -> {
                    if (node.has("content")) {
                        JsonNode content = node.get("content");
                        if (content.isArray()) {
                            StringBuilder text = new StringBuilder();
                            for (JsonNode item : content) {
                                if (item.has("text")) {
                                    text.append(item.get("text").asText());
                                }
                            }
                            if (!text.isEmpty()) {
                                dto.setFinalResponse(text.toString());
                            }
                        }
                    }
                }

                // 工具调用完成
                case "item_completed" -> {
                    List<String> items = dto.getNewItemsJson();
                    if (items == null) {
                        items = new ArrayList<>();
                        dto.setNewItemsJson(items);
                    }
                    items.add(rawJson);
                }

                // Token 统计
                case "token_count" -> {
                    if (node.has("info")) {
                        JsonNode info = node.get("info");
                        TaskResponse.Usage usage = new TaskResponse.Usage();

                        if (info.has("last_token_usage")) {
                            JsonNode lastUsage = info.get("last_token_usage");
                            usage.promptTokens = lastUsage.has("input_tokens")
                                    ? lastUsage.get("input_tokens").asLong() : 0;
                            usage.completionTokens = lastUsage.has("output_tokens")
                                    ? String.valueOf(lastUsage.get("output_tokens").asLong()) : "0";
                            usage.totalTokens = lastUsage.has("total_tokens")
                                    ? lastUsage.get("total_tokens").asLong() : 0;
                        }

                        dto.setUsage(usage);
                    }
                }

                // 其他事件类型可以根据需要添加
                default -> {
                    // 对于未明确处理的事件，可以选择忽略或记录日志
                    log.trace("未处理的事件类型: {}", eventType);
                }
            }
        } catch (Exception e) {
            log.warn("解析事件失败: eventType={}, error={}", eventType, e.getMessage());
        }
    }
}

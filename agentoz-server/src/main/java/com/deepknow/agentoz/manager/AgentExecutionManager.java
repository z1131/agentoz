package com.deepknow.agentoz.manager;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.converter.grpc.ConfigProtoConverter;
import com.deepknow.agentoz.infra.converter.grpc.InternalCodexEventConverter;
import com.deepknow.agentoz.infra.history.AgentContextManager;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.ConversationEntity;
import codex.agent.RunTaskRequest;
import codex.agent.SessionConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.apache.dubbo.common.stream.StreamObserver;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.List;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutionManager {

    private final AgentRepository agentRepository;

    private final ConversationRepository conversationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());


    /**
     * 持久化 Codex 事件到会话历史
     * 公开方法，供外部调用
     */
    public void persistEvent(String conversationId, String agentId, String senderName, InternalCodexEvent event) {
        persist(conversationId, agentId, senderName, event);
    }

    /**
     * 更新 Agent 的 activeContext
     * 公开方法，供 CallAgentTool 等外部调用
     */
    public void updateAgentActiveContext(String agentId, byte[] rolloutBytes) {
        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );
            if (agent != null) {
                agent.setActiveContextFromBytes(rolloutBytes);
                agentRepository.updateById(agent);
                log.info("Agent activeContext 已更新: agentId={}, size={} bytes", agentId, rolloutBytes != null ? rolloutBytes.length : 0);
            }
        } catch (Exception e) {
            log.error("更新 Agent activeContext 失败: agentId={}", agentId, e);
        }
    }


    private void persist(String conversationId, String agentId, String senderName, InternalCodexEvent event) {
        try {
            if (event.getEventType() == null || event.getRawEventJson() == null) return;
            JsonNode n = objectMapper.readTree(event.getRawEventJson());
            ObjectNode item = null;
            if ("agent_message".equals(event.getEventType())) item = createAgentMsg(senderName, n);
            else if ("item.completed".equals(event.getEventType())) item = createToolItem(senderName, n);
            else if ("agent_reasoning".equals(event.getEventType())) item = createReasoningItem(senderName, n);
            else item = createGenericItem(senderName, n, event.getEventType());
            if (item != null) {
                appendHistoryItem(conversationId, item);
                appendAgentHistory(agentId, item);
                if (event.getDisplayItems() == null) event.setDisplayItems(new java.util.ArrayList<>());
                event.getDisplayItems().add(objectMapper.writeValueAsString(item));
            }
        } catch (Exception ignored) {}
    }

    private void appendAgentHistory(String agentId, ObjectNode item) {
        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
            );
            if (agent == null) return;
            ArrayNode h = (agent.getFullHistory() == null || agent.getFullHistory().isEmpty() || "null".equals(agent.getFullHistory())) ? objectMapper.createArrayNode() : (ArrayNode) objectMapper.readTree(agent.getFullHistory());
            h.add(item);
            agent.setFullHistory(objectMapper.writeValueAsString(h));
            agentRepository.updateById(agent);
            log.debug("Agent fullHistory 已更新: agentId={}, items={}", agentId, h.size());
        } catch (Exception e) {
            log.warn("更新 Agent fullHistory 失败: agentId={}", agentId, e);
        }
    }

    private ObjectNode createAgentMsg(String senderName, JsonNode n) {
        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", n);
        item.put("type", "agent_message");
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private ObjectNode createToolItem(String senderName, JsonNode n) {
        JsonNode itemNode = n.path("item");
        if (itemNode.isMissingNode()) return null;
        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", itemNode);
        item.put("type", "item.completed");
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private ObjectNode createReasoningItem(String senderName, JsonNode n) {
        ObjectNode item = objectMapper.createObjectNode();
        ObjectNode reasoningContent = objectMapper.createObjectNode();
        reasoningContent.put("type", "text");
        reasoningContent.put("text", n.path("content").asText(""));
        item.set("item", reasoningContent);
        item.put("type", "agent_reasoning");
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private ObjectNode createGenericItem(String senderName, JsonNode n, String eventType) {
        ObjectNode item = objectMapper.createObjectNode();
        item.set("item", n);
        item.put("type", eventType);
        item.put("agentId", n.has("sender_id") ? n.get("sender_id").asText() : "");
        item.put("agentName", senderName);
        return item;
    }

    private void appendHistoryItem(String cid, ObjectNode i) {
        try {
            ConversationEntity c = conversationRepository.selectOne(new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getConversationId, cid));
            if (c == null) return;
            ArrayNode h = (c.getHistoryContext() == null || c.getHistoryContext().isEmpty() || "null".equals(c.getHistoryContext())) ? objectMapper.createArrayNode() : (ArrayNode) objectMapper.readTree(c.getHistoryContext());
            h.add(i); c.setHistoryContext(objectMapper.writeValueAsString(h));
            if ("agent_message".equals(i.get("type").asText())) {
                String text = i.path("item").path("text").asText("");
                c.setLastMessageContent(trunc(text, 500)); c.setLastMessageType("assistant");
            }
            c.setLastMessageAt(LocalDateTime.now()); c.setMessageCount((c.getMessageCount() != null ? c.getMessageCount() : 0) + 1);
            conversationRepository.updateById(c);
        } catch (Exception ignored) {}
    }

    /**
     * 截断字符串到指定长度
     */
    private String trunc(String str, int maxLen) {
        if (str == null) return null;
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}

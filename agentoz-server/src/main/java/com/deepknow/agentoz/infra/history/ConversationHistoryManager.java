package com.deepknow.agentoz.infra.history;

import codex.agent.HistoryItem;
import codex.agent.MessageItem;
import codex.agent.ContentItem;
import com.deepknow.agentoz.model.ConversationEntity;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 会话历史管理器
 *
 * <p>负责管理会话级别的历史记录，包括：</p>
 * <ul>
 *   <li>追加新的历史项到会话</li>
 *   <li>更新会话的辅助字段（消息数、最后一条消息等）</li>
 *   <li>立即持久化到数据库</li>
 * </ul>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConversationHistoryManager {

    @Autowired
    private ConversationRepository conversationRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 追加用户消息到会话历史
     *
     * @param conversationId 会话ID
     * @param userMessage 用户消息内容
     */
    public void appendUserMessage(String conversationId, String userMessage) {
        log.info(">>> 追加用户消息到会话: conversationId={}, messageLength={}",
                conversationId, userMessage != null ? userMessage.length() : 0);

        // 构建用户消息的 HistoryItem
        MessageItem messageItem = MessageItem.newBuilder()
                .setRole("user")
                .addContent(ContentItem.newBuilder().setText(userMessage).build())
                .build();

        HistoryItem historyItem = HistoryItem.newBuilder()
                .setMessage(messageItem)
                .build();

        appendHistoryItem(conversationId, historyItem);
    }

    /**
     * 追加 Assistant 响应到会话历史
     *
     * @param conversationId 会话ID
     * @param assistantMessage Assistant 响应内容
     */
    public void appendAssistantMessage(String conversationId, String assistantMessage) {
        log.info(">>> 追加Assistant响应到会话: conversationId={}, messageLength={}",
                conversationId, assistantMessage != null ? assistantMessage.length() : 0);

        // 构建 Assistant 消息的 HistoryItem
        MessageItem messageItem = MessageItem.newBuilder()
                .setRole("assistant")
                .addContent(ContentItem.newBuilder().setText(assistantMessage).build())
                .build();

        HistoryItem historyItem = HistoryItem.newBuilder()
                .setMessage(messageItem)
                .build();

        appendHistoryItem(conversationId, historyItem);
    }

    /**
     * 追加函数调用记录到会话历史
     *
     * @param conversationId 会话ID
     * @param callId 调用ID
     * @param functionName 函数名称
     * @param arguments 函数参数（JSON字符串）
     */
    public void appendFunctionCall(String conversationId, String callId, String functionName, String arguments) {
        log.info(">>> 追加函数调用到会话: conversationId={}, callId={}, function={}",
                conversationId, callId, functionName);

        codex.agent.FunctionCallItem functionCallItem = codex.agent.FunctionCallItem.newBuilder()
                .setCallId(callId)
                .setName(functionName)
                .setArguments(arguments)
                .build();

        HistoryItem historyItem = HistoryItem.newBuilder()
                .setFunctionCall(functionCallItem)
                .build();

        appendHistoryItem(conversationId, historyItem);
    }

    /**
     * 追加函数返回结果到会话历史
     *
     * @param conversationId 会话ID
     * @param callId 调用ID
     * @param output 函数返回结果（JSON字符串）
     */
    public void appendFunctionCallOutput(String conversationId, String callId, String output) {
        log.info(">>> 追加函数返回到会话: conversationId={}, callId={}", conversationId, callId);

        codex.agent.FunctionCallOutputItem functionCallOutputItem = codex.agent.FunctionCallOutputItem.newBuilder()
                .setCallId(callId)
                .setOutput(output)
                .build();

        HistoryItem historyItem = HistoryItem.newBuilder()
                .setFunctionCallOutput(functionCallOutputItem)
                .build();

        appendHistoryItem(conversationId, historyItem);
    }

    /**
     * 追加 HistoryItem 到会话历史（核心方法）
     *
     * @param conversationId 会话ID
     * @param newItem 要追加的历史项
     */
    private void appendHistoryItem(String conversationId, HistoryItem newItem) {
        try {
            // 1. 查询会话
            ConversationEntity conversation = conversationRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getConversationId, conversationId)
            );

            if (conversation == null) {
                log.warn("会话不存在，无法追加历史: conversationId={}", conversationId);
                return;
            }

            // 2. 解析现有历史
            List<HistoryItem> historyList = parseHistoryContext(conversation.getHistoryContext());

            // 3. 追加新项
            historyList.add(newItem);

            // 4. 更新会话实体
            conversation.setHistoryContext(serializeHistoryList(historyList));
            conversation.setMessageCount(historyList.size());

            // 5. 更新辅助字段（从新项中提取）
            updateConversationMetadata(conversation, newItem);

            // 6. 立即写库
            conversationRepository.updateById(conversation);

            log.debug("✅ 会话历史已更新并写库: conversationId={}, messageCount={}",
                    conversationId, historyList.size());

        } catch (Exception e) {
            log.error("❌ 追加会话历史失败: conversationId={}", conversationId, e);
            // 不抛出异常，避免影响主流程
        }
    }

    /**
     * 解析历史上下文 JSON 字符串为 HistoryItem 列表
     */
    private List<HistoryItem> parseHistoryContext(String historyContextJson) {
        if (historyContextJson == null || historyContextJson.isEmpty() || "null".equals(historyContextJson)) {
            return new ArrayList<>();
        }

        try {
            // 注意：这里需要将 HistoryItem 序列化为 JSON
            // 由于 Proto 的 JSON 序列化比较复杂，我们使用简化的格式
            // 实际使用时可能需要根据 Codex-Agent 返回的 new_items_json 格式调整

            // 临时方案：直接解析 Codex-Agent 返回的 JSON 格式
            return new ArrayList<>(); // TODO: 实现解析逻辑

        } catch (Exception e) {
            log.warn("解析历史上下文失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将 HistoryItem 列表序列化为 JSON 字符串
     */
    private String serializeHistoryList(List<HistoryItem> historyList) {
        try {
            // TODO: 实现 HistoryItem 到 JSON 的序列化
            // 临时返回空数组
            return "[]";
        } catch (Exception e) {
            log.error("序列化历史列表失败", e);
            return "[]";
        }
    }

    /**
     * 从 HistoryItem 中提取元数据并更新 ConversationEntity
     */
    private void updateConversationMetadata(ConversationEntity conversation, HistoryItem newItem) {
        conversation.setLastMessageAt(LocalDateTime.now());

        // 提取消息类型
        if (newItem.hasMessage()) {
            conversation.setLastMessageType("message");
            // 提取文本内容
            if (newItem.getMessage().getContentCount() > 0) {
                String text = newItem.getMessage().getContent(0).getText();
                conversation.setLastMessageContent(truncateText(text, 500)); // 限制长度
            }
        } else if (newItem.hasFunctionCall()) {
            conversation.setLastMessageType("function_call");
            conversation.setLastMessageContent("调用: " + newItem.getFunctionCall().getName());
        } else if (newItem.hasFunctionCallOutput()) {
            conversation.setLastMessageType("function_call_output");
            conversation.setLastMessageContent("函数返回");
        }

        // 更新格式版本
        if (conversation.getHistoryFormat() == null) {
            conversation.setHistoryFormat("history_items_v1");
        }
    }

    /**
     * 截断文本到指定长度
     */
    private String truncateText(String text, int maxLength) {
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}

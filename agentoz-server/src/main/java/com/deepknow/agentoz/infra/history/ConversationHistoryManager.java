package com.deepknow.agentoz.infra.history;

import codex.agent.HistoryItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.infra.repo.ConversationMessageRepository;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import com.deepknow.agentoz.model.ConversationEntity;
import com.deepknow.agentoz.model.ConversationMessageEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 会话历史管理器
 *
 * <p>负责管理会话级别的历史记录，包括：</p>
 * <ul>
 *   <li>追加新的消息到 conversation_messages 表</li>
 *   <li>更新会话的辅助字段（消息数、最后一条消息等）</li>
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

    @Autowired
    private ConversationMessageRepository messageRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 追加用户消息到会话历史
     *
     * @param conversationId 会话ID
     * @param userMessage 用户消息内容
     * @param roleName 角色名称 (如 "user" 或具体的 AgentName)
     */
    public void appendUserMessage(String conversationId, String userMessage, String roleName) {
        log.info("追加消息到会话: conversationId={}, role={}", conversationId, roleName);

        ConversationMessageEntity message = ConversationMessageEntity.builder()
                .conversationId(conversationId)
                .role("user") // 语义角色
                .senderName(roleName != null ? roleName : "user") // 显示名称
                .content(userMessage)
                .createdAt(LocalDateTime.now())
                .build();

        saveMessageAndUpdateConversation(message);
    }

    /**
     * 追加 Assistant 响应到会话历史
     *
     * @param conversationId 会话ID
     * @param assistantMessage Assistant 响应内容
     * @param roleName 角色名称 (通常为 AgentName)
     */
    public void appendAssistantMessage(String conversationId, String assistantMessage, String roleName) {
        log.info("追加Assistant响应: conversationId={}, role={}", conversationId, roleName);

        ConversationMessageEntity message = ConversationMessageEntity.builder()
                .conversationId(conversationId)
                .role("assistant")
                .senderName(roleName != null ? roleName : "assistant")
                .content(assistantMessage)
                .createdAt(LocalDateTime.now())
                .build();

        saveMessageAndUpdateConversation(message);
    }

    /**
     * 追加函数调用记录到会话历史
     */
    public void appendFunctionCall(String conversationId, String callId, String functionName, String arguments) {
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("type", "function_call");
            meta.put("call_id", callId);
            meta.put("name", functionName);
            meta.put("arguments", arguments);

            ConversationMessageEntity message = ConversationMessageEntity.builder()
                    .conversationId(conversationId)
                    .role("tool_call")
                    .senderName("System")
                    .content("调用工具: " + functionName)
                    .metaData(objectMapper.writeValueAsString(meta))
                    .createdAt(LocalDateTime.now())
                    .build();

            saveMessageAndUpdateConversation(message);
        } catch (Exception e) {
            log.error("追加函数调用失败", e);
        }
    }

    /**
     * 追加函数返回结果到会话历史
     */
    public void appendFunctionCallOutput(String conversationId, String callId, String output) {
        try {
            Map<String, Object> meta = new HashMap<>();
            meta.put("type", "function_call_output");
            meta.put("call_id", callId);
            // output 可能很长，考虑截断或只存 meta
            
            ConversationMessageEntity message = ConversationMessageEntity.builder()
                    .conversationId(conversationId)
                    .role("tool_output")
                    .senderName("System")
                    .content("工具返回结果")
                    .metaData(objectMapper.writeValueAsString(meta))
                    .createdAt(LocalDateTime.now())
                    .build();

            saveMessageAndUpdateConversation(message);
        } catch (Exception e) {
            log.error("追加函数返回失败", e);
        }
    }

    /**
     * 保存消息并更新会话元数据
     */
    private void saveMessageAndUpdateConversation(ConversationMessageEntity message) {
        try {
            // 1. 插入消息表 (原子操作，快)
            messageRepository.insert(message);

            // 2. 更新会话主表 (异步或同步均可，为了数据一致性这里用同步)
            ConversationEntity conversation = conversationRepository.selectOne(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getConversationId, message.getConversationId())
            );

            if (conversation != null) {
                conversation.setLastMessageAt(LocalDateTime.now());
                conversation.setLastMessageType(message.getRole());
                conversation.setLastMessageContent(truncateText(message.getContent(), 500));
                
                // 简单的计数器增加 (非严格准确，但足够用)
                Integer count = conversation.getMessageCount();
                conversation.setMessageCount(count != null ? count + 1 : 1);
                
                conversationRepository.updateById(conversation);
            }
        } catch (Exception e) {
            log.error("保存会话消息失败: cid={}", message.getConversationId(), e);
        }
    }

    private String truncateText(String text, int maxLength) {
        if (text == null) return null;
        return text.length() <= maxLength ? text : text.substring(0, maxLength) + "...";
    }
}

package com.deepknow.agentoz.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.model.ConversationEntity;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 会话历史服务
 *
 * <p>负责管理会话的对话历史记录</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationHistoryService {

    private final ConversationRepository conversationRepository;
    private final ObjectMapper objectMapper;

    /**
     * 添加用户消息到历史记录
     *
     * @param conversationId 会话ID
     * @param userMessage 用户消息内容
     */
    public void appendUserMessage(String conversationId, String userMessage) {
        log.info("[History] 添加用户消息: convId={}, message={}", conversationId, userMessage);

        try {
            // 1. 查询会话
            ConversationEntity conversation = conversationRepository.selectOne(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getConversationId, conversationId)
            );

            if (conversation == null) {
                log.warn("[History] 会话不存在: convId={}", conversationId);
                return;
            }

            // 2. 解析现有历史记录
            ArrayNode historyArray = parseHistoryArray(conversation.getHistoryContext());

            // 3. 添加用户消息
            ObjectNode userMsgNode = objectMapper.createObjectNode();
            userMsgNode.put("role", "user");
            userMsgNode.put("content", userMessage);
            userMsgNode.put("timestamp", LocalDateTime.now().toString());

            historyArray.add(userMsgNode);

            // 4. 更新数据库
            String newHistoryContext = objectMapper.writeValueAsString(historyArray);
            conversation.setHistoryContext(newHistoryContext);
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversation.setLastMessageContent(userMessage);
            conversation.setLastMessageType("user");
            conversation.setLastMessageAt(LocalDateTime.now());
            conversation.setLastActivityAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());

            conversationRepository.updateById(conversation);

            log.info("[History] 用户消息已保存: convId={}, totalCount={}", conversationId, conversation.getMessageCount());

        } catch (Exception e) {
            log.error("[History] 保存用户消息失败: convId={}", conversationId, e);
        }
    }

    /**
     * 添加Agent回复到历史记录
     *
     * @param conversationId 会话ID
     * @param agentName Agent名称
     * @param agentReply Agent回复内容
     */
    public void appendAgentReply(String conversationId, String agentName, String agentReply) {
        log.info("[History] 添加Agent回复: convId={}, agent={}, replyLength={}",
                conversationId, agentName, agentReply != null ? agentReply.length() : 0);

        try {
            // 1. 查询会话
            ConversationEntity conversation = conversationRepository.selectOne(
                    new LambdaQueryWrapper<ConversationEntity>()
                            .eq(ConversationEntity::getConversationId, conversationId)
            );

            if (conversation == null) {
                log.warn("[History] 会话不存在: convId={}", conversationId);
                return;
            }

            // 2. 解析现有历史记录
            ArrayNode historyArray = parseHistoryArray(conversation.getHistoryContext());

            // 3. 添加Agent回复
            ObjectNode agentMsgNode = objectMapper.createObjectNode();
            agentMsgNode.put("role", "assistant");
            agentMsgNode.put("name", agentName);
            agentMsgNode.put("content", agentReply != null ? agentReply : "");
            agentMsgNode.put("timestamp", LocalDateTime.now().toString());

            historyArray.add(agentMsgNode);

            // 4. 更新数据库
            String newHistoryContext = objectMapper.writeValueAsString(historyArray);
            conversation.setHistoryContext(newHistoryContext);
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversation.setLastMessageContent(agentReply != null ? agentReply : "");
            conversation.setLastMessageType("assistant");
            conversation.setLastMessageAt(LocalDateTime.now());
            conversation.setLastActivityAt(LocalDateTime.now());
            conversation.setUpdatedAt(LocalDateTime.now());

            conversationRepository.updateById(conversation);

            log.info("[History] Agent回复已保存: convId={}, agent={}, totalCount={}",
                    conversationId, agentName, conversation.getMessageCount());

        } catch (Exception e) {
            log.error("[History] 保存Agent回复失败: convId={}, agent={}", conversationId, agentName, e);
        }
    }

    /**
     * 解析历史记录JSON数组
     *
     * @param historyContext 历史记录JSON字符串
     * @return 解析后的ArrayNode
     */
    private ArrayNode parseHistoryArray(String historyContext) {
        try {
            if (historyContext == null || historyContext.isEmpty() || "null".equals(historyContext)) {
                return objectMapper.createArrayNode();
            }

            JsonNode jsonNode = objectMapper.readTree(historyContext);
            if (jsonNode.isArray()) {
                return (ArrayNode) jsonNode;
            } else {
                log.warn("[History] 历史记录格式错误,不是数组: {}", historyContext);
                return objectMapper.createArrayNode();
            }
        } catch (Exception e) {
            log.error("[History] 解析历史记录失败: {}", historyContext, e);
            return objectMapper.createArrayNode();
        }
    }
}

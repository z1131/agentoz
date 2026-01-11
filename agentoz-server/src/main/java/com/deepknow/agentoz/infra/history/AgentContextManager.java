package com.deepknow.agentoz.infra.history;

import codex.agent.HistoryItem;
import codex.agent.MessageItem;
import codex.agent.ContentItem;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 上下文管理器
 *
 * <p>负责管理单个 Agent 的交互历史和状态描述：</p>
 * <ul>
 *   <li>追加新的交互项到 Agent 上下文</li>
 *   <li>更新 Agent 的状态描述（stateDescription）</li>
 *   <li>立即持久化到数据库</li>
 * </ul>
 *
 * @author AgentOZ Team
 * @since 1.0.0
 */
@Slf4j
@Component
public class AgentContextManager {

    @Autowired
    private AgentRepository agentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Agent 被调用时更新状态
     *
     * <p>执行以下操作：</p>
     * <ol>
     *   <li>追加输入消息到 activeContext</li>
     *   <li>更新 stateDescription（输入摘要）</li>
     *   <li>更新交互统计</li>
     *   <li>立即写库</li>
     * </ol>
     *
     * @param agentId Agent ID
     * @param inputMessage 输入消息内容
     */
    public void onAgentCalled(String agentId, String inputMessage) {
        log.info(">>> Agent被调用: agentId={}, inputLength={}",
                agentId, inputMessage != null ? inputMessage.length() : 0);

        try {
            // 1. 查询 Agent
            AgentEntity agent = agentRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                log.warn("Agent不存在，无法更新状态: agentId={}", agentId);
                return;
            }

            // 2. 追加输入消息到 activeContext
            MessageItem messageItem = MessageItem.newBuilder()
                    .setRole("user")
                    .addContent(ContentItem.newBuilder().setText(inputMessage).build())
                    .build();

            HistoryItem historyItem = HistoryItem.newBuilder()
                    .setMessage(messageItem)
                    .build();

            appendToAgentContext(agent, historyItem);

            // 3. 更新 stateDescription（输入摘要）
            String inputSummary = generateInputSummary(inputMessage);
            if (agent.getStateDescription() == null || agent.getStateDescription().isEmpty()) {
                agent.setStateDescription("输入: " + inputSummary);
            } else {
                // 如果已有描述，追加新的输入
                agent.setStateDescription(agent.getStateDescription() + " | 新输入: " + inputSummary);
            }

            // 4. 更新交互统计
            agent.setInteractionCount((agent.getInteractionCount() != null ? agent.getInteractionCount() : 0) + 1);
            agent.setLastInteractionType("input");
            agent.setLastInteractionAt(LocalDateTime.now());

            // 5. 设置上下文格式版本（首次）
            if (agent.getContextFormat() == null) {
                agent.setContextFormat("history_items_v1");
            }

            // 6. 立即写库
            agentRepository.updateById(agent);

            log.info("✅ Agent被调用状态已更新: agentId={}, stateDescription={}",
                    agentId, agent.getStateDescription());

        } catch (Exception e) {
            log.error("❌ 更新Agent被调用状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * Agent 返回响应时更新状态
     *
     * <p>执行以下操作：</p>
     * <ol>
     *   <li>追加响应消息到 activeContext</li>
     *   <li>更新 stateDescription（追加结果摘要）</li>
     *   <li>更新交互统计</li>
     *   <li>立即写库</li>
     * </ol>
     *
     * @param agentId Agent ID
     * @param responseMessage 响应消息内容
     */
    public void onAgentResponse(String agentId, String responseMessage) {
        log.info(">>> Agent返回响应: agentId={}, responseLength={}",
                agentId, responseMessage != null ? responseMessage.length() : 0);

        try {
            // 1. 查询 Agent
            AgentEntity agent = agentRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                log.warn("Agent不存在，无法更新状态: agentId={}", agentId);
                return;
            }

            // 2. 追加响应消息到 activeContext
            MessageItem messageItem = MessageItem.newBuilder()
                    .setRole("assistant")
                    .addContent(ContentItem.newBuilder().setText(responseMessage).build())
                    .build();

            HistoryItem historyItem = HistoryItem.newBuilder()
                    .setMessage(messageItem)
                    .build();

            appendToAgentContext(agent, historyItem);

            // 3. 更新 stateDescription（追加结果摘要）
            String resultSummary = generateResultSummary(responseMessage);
            String currentDesc = agent.getStateDescription();
            if (currentDesc == null || currentDesc.isEmpty()) {
                agent.setStateDescription("输出: " + resultSummary);
            } else {
                // 追加结果到现有描述
                agent.setStateDescription(currentDesc + " | 输出: " + resultSummary);
            }

            // 4. 更新交互统计
            agent.setInteractionCount((agent.getInteractionCount() != null ? agent.getInteractionCount() : 0) + 1);
            agent.setLastInteractionType("output");
            agent.setLastInteractionAt(LocalDateTime.now());

            // 5. 立即写库
            agentRepository.updateById(agent);

            log.info("✅ Agent返回响应状态已更新: agentId={}, stateDescription={}",
                    agentId, agent.getStateDescription());

        } catch (Exception e) {
            log.error("❌ 更新Agent返回响应状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * Agent 调用工具时更新状态
     *
     * @param agentId Agent ID
     * @param callId 调用ID
     * @param toolName 工具名称
     * @param arguments 工具参数（JSON字符串）
     */
    public void onAgentCalledTool(String agentId, String callId, String toolName, String arguments) {
        log.info(">>> Agent调用工具: agentId={}, callId={}, tool={}", agentId, callId, toolName);

        try {
            AgentEntity agent = agentRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                return;
            }

            // 追加函数调用记录到 activeContext
            codex.agent.FunctionCallItem functionCallItem = codex.agent.FunctionCallItem.newBuilder()
                    .setCallId(callId)
                    .setName(toolName)
                    .setArguments(arguments)
                    .build();

            HistoryItem historyItem = HistoryItem.newBuilder()
                    .setFunctionCall(functionCallItem)
                    .build();

            appendToAgentContext(agent, historyItem);

            // 更新 stateDescription（追加工具调用信息）
            String currentDesc = agent.getStateDescription();
            if (currentDesc == null || currentDesc.isEmpty()) {
                agent.setStateDescription("调用工具: " + toolName);
            } else {
                agent.setStateDescription(currentDesc + " | 调用工具: " + toolName);
            }

            agentRepository.updateById(agent);

        } catch (Exception e) {
            log.error("❌ 更新Agent工具调用状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * 工具返回结果时更新状态
     *
     * @param agentId Agent ID
     * @param callId 调用ID
     * @param output 工具返回结果（JSON字符串）
     */
    public void onToolReturned(String agentId, String callId, String output) {
        log.info(">>> 工具返回结果: agentId={}, callId={}", agentId, callId);

        try {
            AgentEntity agent = agentRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                return;
            }

            // 追加函数返回结果到 activeContext
            codex.agent.FunctionCallOutputItem functionCallOutputItem = codex.agent.FunctionCallOutputItem.newBuilder()
                    .setCallId(callId)
                    .setOutput(output)
                    .build();

            HistoryItem historyItem = HistoryItem.newBuilder()
                    .setFunctionCallOutput(functionCallOutputItem)
                    .build();

            appendToAgentContext(agent, historyItem);

            agentRepository.updateById(agent);

        } catch (Exception e) {
            log.error("❌ 更新工具返回状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * 追加 HistoryItem 到 Agent 的 activeContext
     */
    private void appendToAgentContext(AgentEntity agent, HistoryItem newItem) {
        List<HistoryItem> contextList = parseAgentContext(agent.getActiveContext());
        contextList.add(newItem);
        agent.setActiveContext(serializeContextList(contextList));
    }

    /**
     * 解析 Agent 上下文 JSON 字符串为 HistoryItem 列表
     */
    private List<HistoryItem> parseAgentContext(String contextJson) {
        if (contextJson == null || contextJson.isEmpty() || "null".equals(contextJson)) {
            return new ArrayList<>();
        }
        try {
            // TODO: 实现 JSON 到 HistoryItem 的解析
            return new ArrayList<>();
        } catch (Exception e) {
            log.warn("解析Agent上下文失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 将 HistoryItem 列表序列化为 JSON 字符串
     */
    private String serializeContextList(List<HistoryItem> contextList) {
        try {
            // TODO: 实现 HistoryItem 到 JSON 的序列化
            return "[]";
        } catch (Exception e) {
            log.error("序列化Agent上下文失败", e);
            return "[]";
        }
    }

    /**
     * 生成输入摘要（用于 stateDescription）
     */
    private String generateInputSummary(String inputMessage) {
        if (inputMessage == null) {
            return "";
        }
        // 简单截断到 50 个字符
        String summary = inputMessage.length() > 50
                ? inputMessage.substring(0, 50) + "..."
                : inputMessage;
        return summary.replace("\n", " "); // 替换换行符
    }

    /**
     * 生成结果摘要（用于 stateDescription）
     */
    private String generateResultSummary(String responseMessage) {
        if (responseMessage == null) {
            return "";
        }
        // 简单截断到 50 个字符
        String summary = responseMessage.length() > 50
                ? responseMessage.substring(0, 50) + "..."
                : responseMessage;
        return summary.replace("\n", " "); // 替换换行符
    }
}

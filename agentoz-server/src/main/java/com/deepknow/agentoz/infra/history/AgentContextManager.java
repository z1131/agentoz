package com.deepknow.agentoz.infra.history;

import codex.agent.ContentItem;
import codex.agent.HistoryItem;
import codex.agent.MessageItem;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.dto.MessageDTO;
import com.deepknow.agentoz.infra.converter.grpc.HistoryProtoConverter;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

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
     * @param agentId Agent ID
     * @param inputMessage 输入消息内容
     * @param role 消息角色 (user 或 assistant)
     */
    public void onAgentCalled(String agentId, String inputMessage, String role) {
        log.info("Agent被调用: agentId={}, role={}, inputLength={}",
                agentId, role, inputMessage != null ? inputMessage.length() : 0);

        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                log.warn("Agent不存在，无法更新状态: agentId={}", agentId);
                return;
            }

            // 1. 构建 Proto (保持内容纯净，不加前缀)
            // LLM 协议强校验 role 必须为 user (对于输入消息)
            MessageItem messageItem = MessageItem.newBuilder()
                    .setRole("user") 
                    .addContent(ContentItem.newBuilder().setText(inputMessage).build())
                    .build();

            HistoryItem historyItem = HistoryItem.newBuilder()
                    .setMessage(messageItem)
                    .build();

            // 2. 转换为 DTO 并存储
            MessageDTO dto = HistoryProtoConverter.toMessageDTO(historyItem);
            agent.appendContext(dto, objectMapper);

            // 3. 更新状态描述 (传入 Role/SenderName 以生成 [From XXX] 的摘要)
            agent.updateInputState(inputMessage, role);

            // 4. 写库
            agentRepository.updateById(agent);

            log.info("Agent被调用状态已更新: agentId={}, stateDescription={}",
                    agentId, agent.getStateDescription());

        } catch (Exception e) {
            log.error("更新Agent被调用状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * Agent 返回响应时更新状态
     */
    public void onAgentResponse(String agentId, String responseMessage) {
        log.info(">>> Agent返回响应: agentId={}, responseLength={}",
                agentId, responseMessage != null ? responseMessage.length() : 0);

        try {
            AgentEntity agent = agentRepository.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                return;
            }

            // 1. 构建 Proto
            MessageItem messageItem = MessageItem.newBuilder()
                    .setRole("assistant")
                    .addContent(ContentItem.newBuilder().setText(responseMessage).build())
                    .build();

            HistoryItem historyItem = HistoryItem.newBuilder()
                    .setMessage(messageItem)
                    .build();

            // 2. 转换为 DTO 并存储
            MessageDTO dto = HistoryProtoConverter.toMessageDTO(historyItem);
            agent.appendContext(dto, objectMapper);

            // 3. 更新状态
            agent.updateOutputState(responseMessage);

            // 4. 写库
            agentRepository.updateById(agent);

            log.info("✅ Agent返回响应状态已更新: agentId={}", agentId);

        } catch (Exception e) {
            log.error("❌ 更新Agent返回响应状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * Agent 调用工具时更新状态
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

            // 构建简单的 Map 存储工具调用记录 (暂不使用 Proto 转换)
            Map<String, Object> toolCall = new HashMap<>();
            toolCall.put("type", "function_call");
            toolCall.put("call_id", callId);
            toolCall.put("name", toolName);
            toolCall.put("arguments", arguments);

            agent.appendContext(toolCall, objectMapper);
            
            // 更新状态描述 (简单追加)
            String currentDesc = agent.getStateDescription();
            String summary = "调用工具: " + toolName;
            if (currentDesc == null || currentDesc.isEmpty()) {
                agent.setStateDescription(summary);
            } else {
                agent.setStateDescription(currentDesc + " | " + summary);
            }

            agentRepository.updateById(agent);

        } catch (Exception e) {
            log.error("❌ 更新Agent工具调用状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * 工具返回结果时更新状态
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

            // 构建 Map 存储结果
            Map<String, Object> toolOutput = new HashMap<>();
            toolOutput.put("type", "function_call_output");
            toolOutput.put("call_id", callId);
            toolOutput.put("output", output);

            agent.appendContext(toolOutput, objectMapper);
            agentRepository.updateById(agent);

        } catch (Exception e) {
            log.error("❌ 更新工具返回状态失败: agentId={}", agentId, e);
        }
    }
}
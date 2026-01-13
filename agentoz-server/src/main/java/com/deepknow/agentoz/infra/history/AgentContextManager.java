package com.deepknow.agentoz.infra.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

            // 1. 直接构造 ResponseItem 格式 (符合 Codex 定义)
            // 格式: {"type": "message", "role": "user", "content": [{"type": "input_text", "text": "..."}]}
            ObjectNode responseItem = objectMapper.createObjectNode();
            responseItem.put("type", "message");
            responseItem.put("role", role != null ? role : "user");

            // 构造 content 数组
            ObjectNode contentItem = objectMapper.createObjectNode();
            contentItem.put("type", "input_text");
            contentItem.put("text", inputMessage);
            responseItem.set("content", objectMapper.createArrayNode().add(contentItem));

            // 2. 将 JSON 字符串追加到 Agent 上下文
            agent.appendContext(responseItem.toString(), objectMapper);

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
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                return;
            }

            // 1. 直接构造 ResponseItem 格式 (符合 Codex 定义)
            // 格式: {"type": "message", "role": "assistant", "content": [{"type": "output_text", "text": "..."}]}
            ObjectNode responseItem = objectMapper.createObjectNode();
            responseItem.put("type", "message");
            responseItem.put("role", "assistant");

            // 构造 content 数组
            ObjectNode contentItem = objectMapper.createObjectNode();
            contentItem.put("type", "output_text");
            contentItem.put("text", responseMessage);
            responseItem.set("content", objectMapper.createArrayNode().add(contentItem));

            // 2. 将 JSON 字符串追加到 Agent 上下文
            agent.appendContext(responseItem.toString(), objectMapper);

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

            // 直接构造 ResponseItem 格式 (符合 Codex 定义)
            // 格式: {"type": "function_call", "call_id": "...", "name": "...", "arguments": "..."}
            ObjectNode responseItem = objectMapper.createObjectNode();
            responseItem.put("type", "function_call");
            responseItem.put("call_id", callId);
            responseItem.put("name", toolName);
            responseItem.put("arguments", arguments);

            // 将 JSON 字符串追加到 Agent 上下文
            agent.appendContext(responseItem.toString(), objectMapper);
            
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

            // 直接构造 ResponseItem 格式 (符合 Codex 定义)
            // 格式: {"type": "function_call_output", "call_id": "...", "output": "..."}
            ObjectNode responseItem = objectMapper.createObjectNode();
            responseItem.put("type", "function_call_output");
            responseItem.put("call_id", callId);
            responseItem.put("output", output);

            // 将 JSON 字符串追加到 Agent 上下文
            agent.appendContext(responseItem.toString(), objectMapper);
            agentRepository.updateById(agent);

        } catch (Exception e) {
            log.error("❌ 更新工具返回状态失败: agentId={}", agentId, e);
        }
    }
}
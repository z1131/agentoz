package com.deepknow.agentoz.infra.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent 上下文管理器（新版设计）
 *
 * <h3>🔄 职责变化说明</h3>
 * <p>在新版设计中，Agent 的 activeContext 由 Codex 直接管理（JSONL bytes），
 * 因此此类不再负责追加上下文内容，而是专注于：</p>
 * <ul>
 *   <li>更新 Agent 的状态描述（stateDescription）- 用于 UI 展示</li>
 *   <li>更新 Agent 的交互统计（interactionCount, lastInteractionType）</li>
 *   <li>管理 fullHistory（全量历史，用于审计，与 Codex 无关）</li>
 * </ul>
 *
 * <h3>⚠️ 重要提示</h3>
 * <p>activeContext 的更新由 {@code AgentExecutionServiceImpl} 在收到 Codex 的
 * updated_rollout 事件后直接处理，不经过此类。</p>
 *
 * @author AgentOZ Team
 * @since 2.0.0
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
     * <p>⚠️ 此方法仅更新状态描述，不再追加 activeContext</p>
     *
     * @param agentId Agent ID
     * @param inputMessage 输入消息内容
     * @param role 消息角色 (user 或 caller agent name)
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

            // 仅更新状态描述（用于 UI 展示）
            agent.updateInputState(inputMessage, role);

            // 持久化
            agentRepository.updateById(agent);

            log.info("Agent被调用状态已更新: agentId={}, stateDescription={}",
                    agentId, agent.getStateDescription());

        } catch (Exception e) {
            log.error("更新Agent被调用状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * Agent 返回响应时更新状态
     *
     * <p>⚠️ 此方法仅更新状态描述，activeContext 由服务层直接处理 updated_rollout</p>
     */
    public void onAgentResponse(String agentId, String responseMessage) {
        log.info("Agent返回响应: agentId={}, responseLength={}",
                agentId, responseMessage != null ? responseMessage.length() : 0);

        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                return;
            }

            // 仅更新状态描述（用于 UI 展示）
            agent.updateOutputState(responseMessage);

            // 持久化
            agentRepository.updateById(agent);

            log.info("Agent返回响应状态已更新: agentId={}", agentId);

        } catch (Exception e) {
            log.error("更新Agent返回响应状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * Agent 调用工具时更新状态描述
     *
     * <p>⚠️ 工具调用记录由 Codex 自动管理在 activeContext 中</p>
     */
    public void onAgentCalledTool(String agentId, String callId, String toolName, String arguments) {
        log.info("Agent调用工具: agentId={}, callId={}, tool={}", agentId, callId, toolName);

        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                return;
            }

            // 仅更新状态描述（用于 UI 展示）
            String currentDesc = agent.getStateDescription();
            String summary = "调用工具: " + toolName;
            if (currentDesc == null || currentDesc.isEmpty()) {
                agent.setStateDescription(summary);
            } else {
                agent.setStateDescription(currentDesc + " | " + summary);
            }

            agentRepository.updateById(agent);

        } catch (Exception e) {
            log.error("更新Agent工具调用状态失败: agentId={}", agentId, e);
        }
    }

    /**
     * 工具返回结果时更新状态描述
     *
     * <p>⚠️ 工具返回记录由 Codex 自动管理在 activeContext 中</p>
     */
    public void onToolReturned(String agentId, String callId, String output) {
        log.info("工具返回结果: agentId={}, callId={}", agentId, callId);

        // 工具返回通常不需要更新状态描述，仅记录日志
        // 如需更新，可以在这里添加逻辑
    }

    /**
     * 更新 Agent 的 activeContext（直接设置 Codex 返回的 rollout）
     *
     * @param agentId Agent ID
     * @param rolloutBytes Codex 返回的 JSONL 字节数据
     */
    public void updateActiveContext(String agentId, byte[] rolloutBytes) {
        try {
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getAgentId, agentId)
            );

            if (agent == null) {
                log.warn("Agent不存在，无法更新上下文: agentId={}", agentId);
                return;
            }

            agent.setActiveContextFromBytes(rolloutBytes);
            agentRepository.updateById(agent);

            log.info("Agent activeContext 已更新: agentId={}, size={} bytes",
                    agentId, rolloutBytes != null ? rolloutBytes.length : 0);

        } catch (Exception e) {
            log.error("更新Agent activeContext失败: agentId={}", agentId, e);
        }
    }
}
package com.deepknow.agentoz.infra.history;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.databind.JsonNode;
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
     * Codex 开始执行时调用
     */
    public void onCodexStart(String agentId) {
        setAgentState(agentId, "Running", "Thinking...");
    }

    /**
     * Codex 结束执行时调用
     */
    public void onCodexStop(String agentId) {
        setAgentState(agentId, "Idle", "Idle");
    }

    /**
     * 处理 Codex 内部事件以更新状态描述
     */
    public void onCodexEvent(String agentId, InternalCodexEvent event) {
        try {
            if (event.getEventType() == null) return;
            
            String description = null;
            
            if ("agent_reasoning".equals(event.getEventType())) {
                // 如果是思考过程，可以显示思考中，或者显示具体的思考内容片段（如果需要）
                // 用户要求取消“人工手写”，直接用 Codex 内容。
                // 暂时保持 Thinking... 或者提取内容前缀
                // JsonNode n = objectMapper.readTree(event.getRawEventJson());
                // String text = n.path("content").asText();
                // if (!text.isEmpty()) description = "Thinking: " + trunc(text, 50);
                description = "Thinking...";
            } else if ("item.completed".equals(event.getEventType())) {
                // 工具调用完成/发起
                JsonNode n = objectMapper.readTree(event.getRawEventJson());
                JsonNode item = n.path("item");
                
                // 仅关注工具调用类型
                if ("mcp_tool_call".equals(item.path("type").asText()) || item.has("tool")) {
                     String toolName = item.path("tool").asText("");
                     String args = item.path("arguments").toString();
                     
                     String action = "Call";
                     String lowerTool = toolName.toLowerCase();
                     if (lowerTool.startsWith("read_") || lowerTool.startsWith("get_")) action = "Read";
                     else if (lowerTool.startsWith("write_") || lowerTool.startsWith("update_") || lowerTool.startsWith("save_")) action = "Write";
                     else if (lowerTool.startsWith("search_")) action = "Search";
                     
                     description = action + ": " + trunc(args, 60);
                }
            }
            
            if (description != null) {
                setAgentState(agentId, "Running", description);
            }
            
        } catch (Exception e) {
            log.warn("处理Codex事件更新状态失败: agentId={}, event={}", agentId, event.getEventType(), e);
        }
    }
    
    private String trunc(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private void setAgentState(String agentId, String state, String description) {
        try {
            AgentEntity agent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
            );
            if (agent != null) {
                if ("Running".equals(state)) {
                    agent.setRunningState(description);
                } else {
                    agent.setIdleState();
                }
                agentRepository.updateById(agent);
            }
        } catch (Exception e) {
            log.error("更新Agent状态失败: agentId={}, state={}", agentId, state, e);
        }
    }

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
        onCodexStart(agentId);
        log.info("Agent被调用: agentId={}, role={}", agentId, role);
    }

    /**
     * Agent 返回响应时更新状态
     *
     * <p>⚠️ 此方法仅更新状态描述，activeContext 由服务层直接处理 updated_rollout</p>
     */
    public void onAgentResponse(String agentId, String responseMessage) {
       onCodexStop(agentId);
       log.info("Agent返回响应: agentId={}", agentId);
    }

    /**
     * Agent 调用工具时更新状态描述
     *
     * <p>⚠️ 工具调用记录由 Codex 自动管理在 activeContext 中</p>
     */
    public void onAgentCalledTool(String agentId, String callId, String toolName, String arguments) {
       // 该方法保留用于兼容旧逻辑，或作为备用
       // 新逻辑倾向于使用 onCodexEvent 统一处理
       // 这里可以转调用 setAgentState 来复用解析逻辑（如果参数更清晰，优先用参数）
        String action = "Call";
        String displayName = toolName.toLowerCase();
        
        if (displayName.startsWith("read_") || displayName.startsWith("get_")) {
            action = "Read";
        } else if (displayName.startsWith("write_") || displayName.startsWith("update_") || displayName.startsWith("delete_") || displayName.startsWith("create_") || displayName.startsWith("save_")) {
            action = "Write";
        } else if (displayName.startsWith("search_")) {
            action = "Search";
        }

        String argsSummary = arguments != null ? arguments : "";
        if (argsSummary.length() > 50) {
            argsSummary = argsSummary.substring(0, 50) + "...";
        }

        String description = action + ": " + argsSummary;
        if (argsSummary.isEmpty()) {
            description = action;
        }
        setAgentState(agentId, "Running", description);
    }

    /**
     * 工具返回结果时更新状态描述
     *
     * <p>⚠️ 工具返回记录由 Codex 自动管理在 activeContext 中</p>
     */
    public void onToolReturned(String agentId, String callId, String output) {
        // 工具返回通常不需要更新状态描述，或者可以更新为 "Thinking..." (工具返回后继续思考)
         setAgentState(agentId, "Running", "Thinking...");
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
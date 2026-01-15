package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.JwtUtils;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.jsonwebtoken.Claims;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Get Active Agents Tool - 获取当前会话的所有活跃 Agent
 */
@Slf4j
@Component
public class GetActiveAgentsTool {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @AgentTool(name = "get_active_agents", description = "获取当前会话中的所有活跃Agent列表及其状态信息。返回Agent名称、状态、最后活动时间等。")
    public String getActiveAgents(
            io.modelcontextprotocol.common.McpTransportContext ctx
    ) {
        try {
            // 1. 身份识别，获取 conversationId
            String token = null;
            if (ctx != null) {
                Object securityToken = ctx.get("SECURITY_TOKEN");
                if (securityToken != null) {
                    token = securityToken.toString();
                } else {
                    Object auth = ctx.get("Authorization");
                    if (auth == null) auth = ctx.get("authorization");
                    if (auth != null) token = auth.toString();
                }

                if (token != null && token.startsWith("Bearer ")) {
                    token = token.substring(7);
                }
            }

            String conversationId = null;

            if (token != null) {
                try {
                    Claims claims = jwtUtils.validateToken(token);
                    if (claims != null) {
                        conversationId = claims.get("cid", String.class);
                    }
                } catch (Exception e) {
                    log.warn("Token 解析异常: {}", e.getMessage());
                }
            }

            if (conversationId == null) {
                return "Error: 无法获取当前会话ID，请确保在有效的会话上下文中调用此工具。";
            }

            log.info("MCP GetActiveAgents 调用: ConvId={}", conversationId);

            // 2. 查询该会话下的所有 Agent
            List<AgentEntity> agents = agentRepository.selectList(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
            );

            if (agents == null || agents.isEmpty()) {
                return String.format("当前会话 %s 中暂无 Agent。", conversationId);
            }

            // 3. 按优先级和最后交互时间排序
            List<AgentEntity> sortedAgents = agents.stream()
                    .sorted(Comparator
                            .comparing(AgentEntity::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(AgentEntity::getLastInteractionAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            // 4. 构建返回结果
            StringBuilder result = new StringBuilder();
            result.append(String.format("当前会话共有 %d 个 Agent：\n\n", sortedAgents.size()));

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (int i = 0; i < sortedAgents.size(); i++) {
                AgentEntity agent = sortedAgents.get(i);
                result.append(String.format("%d. %s\n", i + 1, agent.getAgentName()));
                result.append(String.format("   - Agent ID: %s\n", agent.getAgentId()));
                result.append(String.format("   - 状态: %s\n",
                        agent.getState() != null ? agent.getState() : "UNKNOWN"));
                result.append(String.format("   - 描述: %s\n",
                        agent.getDescription() != null && !agent.getDescription().isEmpty() ? agent.getDescription() : "无"));

                if (agent.getStateDescription() != null && !agent.getStateDescription().isEmpty()) {
                    result.append(String.format("   - 当前活动: %s\n", agent.getStateDescription()));
                }

                if (agent.getLastInteractionAt() != null) {
                    result.append(String.format("   - 最后交互时间: %s\n",
                            agent.getLastInteractionAt().format(formatter)));
                }

                if (agent.getInteractionCount() != null && agent.getInteractionCount() > 0) {
                    result.append(String.format("   - 交互次数: %d\n", agent.getInteractionCount()));
                }

                if (agent.getPriority() != null) {
                    result.append(String.format("   - 优先级: %d\n", agent.getPriority()));
                }

                result.append("\n");
            }

            return result.toString();

        } catch (Exception e) {
            log.error("GetActiveAgents 工具执行异常", e);
            return "Error: 工具执行失败 - " + e.getMessage();
        }
    }
}

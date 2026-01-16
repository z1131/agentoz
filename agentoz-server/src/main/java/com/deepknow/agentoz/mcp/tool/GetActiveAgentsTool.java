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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
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

    @AgentTool(name = "get_active_agents", description = "获取当前会话中的所有活跃Agent列表。返回JSON格式的Agent信息，包括名称、ID、描述、状态等。")
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
                return "{\"error\": \"无法获取当前会话ID，请确保在有效的会话上下文中调用此工具。\"}";
            }

            log.info("MCP GetActiveAgents 调用: ConvId={}", conversationId);

            // 2. 查询该会话下的所有 Agent
            List<AgentEntity> agents = agentRepository.selectList(
                    new LambdaQueryWrapper<AgentEntity>()
                            .eq(AgentEntity::getConversationId, conversationId)
            );

            if (agents == null || agents.isEmpty()) {
                Map<String, Object> emptyResult = new HashMap<>();
                emptyResult.put("conversationId", conversationId);
                emptyResult.put("total", 0);
                emptyResult.put("agents", List.of());
                return toJsonString(emptyResult);
            }

            // 3. 按优先级和最后交互时间排序
            List<AgentEntity> sortedAgents = agents.stream()
                    .sorted(Comparator
                            .comparing(AgentEntity::getPriority, Comparator.nullsLast(Comparator.reverseOrder()))
                            .thenComparing(AgentEntity::getLastInteractionAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            // 4. 构建 JSON 结果
            List<Map<String, Object>> agentsJson = sortedAgents.stream()
                    .map(agent -> {
                        Map<String, Object> agentInfo = new HashMap<>();
                        agentInfo.put("agentName", agent.getAgentName());
                        agentInfo.put("agentId", agent.getAgentId());
                        agentInfo.put("description",
                            agent.getDescription() != null && !agent.getDescription().isEmpty()
                                ? agent.getDescription() : "无");
                        agentInfo.put("state",
                            agent.getState() != null ? agent.getState() : "UNKNOWN");
                        agentInfo.put("stateDescription",
                            agent.getStateDescription() != null && !agent.getStateDescription().isEmpty()
                                ? agent.getStateDescription() : "无");
                        return agentInfo;
                    })
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("conversationId", conversationId);
            result.put("total", agentsJson.size());
            result.put("agents", agentsJson);

            return toJsonString(result);

        } catch (Exception e) {
            log.error("GetActiveAgents 工具执行异常", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("error", "工具执行失败");
            errorResult.put("message", e.getMessage());
            return toJsonString(errorResult);
        }
    }

    /**
     * 将 Map 转换为 JSON 字符串
     */
    private String toJsonString(Map<String, Object> data) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("JSON 序列化失败", e);
            return "{\"error\": \"JSON序列化失败\"}";
        }
    }
}

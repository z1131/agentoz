package com.deepknow.agentoz.manager;

import com.deepknow.agentoz.entity.AgentEntity;
import com.deepknow.agentoz.mapper.AgentMapper;
import com.deepknow.agentoz.session.RedisSession;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.memory.InMemoryMemory;
import io.agentscope.core.model.DashScopeChatModel;
import io.agentscope.core.session.Session;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentManager {

    private final AgentMapper agentMapper;
    private final DashScopeChatModel defaultChatModel;

    private final Map<String, AgentEntity> agentDefinitions = new ConcurrentHashMap<>();
    private final Map<String, ReActAgent> agentInstances = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        loadAgentsFromDatabase();
    }

    public void loadAgentsFromDatabase() {
        List<AgentEntity> agents = agentMapper.selectList(null);
        agentDefinitions.clear();
        for (AgentEntity agent : agents) {
            if (Boolean.TRUE.equals(agent.getEnabled())) {
                agentDefinitions.put(agent.getId(), agent);
            }
        }
        log.info("已加载 {} 个Agent定义", agentDefinitions.size());
    }

    /**
     * 获取全局单例 Agent 实例
     */
    public ReActAgent getAgent(String agentId) {
        return agentInstances.computeIfAbsent(agentId, this::createAgentInstance);
    }

    /**
     * 获取 Agent 并加载指定 Session 的状态
     */
    public ReActAgent getAgentWithSession(String agentId, Session session, String sessionId) {
        ReActAgent agent = getAgent(agentId);
        agent.loadIfExists(session, sessionId);
        return agent;
    }

    /**
     * 保存 Agent 状态到 Session
     */
    public void saveAgentSession(String agentId, Session session, String sessionId) {
        ReActAgent agent = agentInstances.get(agentId);
        if (agent != null) {
            agent.saveTo(session, sessionId);
        }
    }

    private ReActAgent createAgentInstance(String agentId) {
        AgentEntity definition = agentDefinitions.get(agentId);
        if (definition == null) {
            throw new IllegalArgumentException("Agent不存在: " + agentId);
        }

        log.info("创建 Agent 实例: {}", agentId);

        // 检查是否有子智能体（Agent as Tool 模式）
        List<String> subAgentIds = definition.getSubAgentIds();
        if (subAgentIds != null && !subAgentIds.isEmpty()) {
            return createAgentWithSubAgents(definition, subAgentIds);
        }

        // 普通智能体（无子智能体）
        return ReActAgent.builder()
                .name(definition.getName())
                .sysPrompt(definition.getSystemPrompt())
                .model(defaultChatModel)
                .memory(new InMemoryMemory())
                .build();
    }

    /**
     * 创建带有子智能体工具的主智能体（Agent as Tool 模式）
     */
    private ReActAgent createAgentWithSubAgents(AgentEntity definition, List<String> subAgentIds) {
        log.info("创建主智能体 [{}]，子智能体: {}", definition.getName(), subAgentIds);

        Toolkit toolkit = new Toolkit();

        for (String subAgentId : subAgentIds) {
            AgentEntity subDef = agentDefinitions.get(subAgentId);
            if (subDef == null) {
                log.warn("子智能体不存在，跳过: {}", subAgentId);
                continue;
            }

            // 生成工具名称：call_xxx
            String toolName = "call_" + subAgentId.replace("-", "_");

            SubAgentConfig config = SubAgentConfig.builder()
                    .toolName(toolName)
                    .description("调用" + subDef.getName() + "：" + subDef.getDescription())
                    .forwardEvents(true)  // 转发子智能体的流式事件
                    .build();

            // 注册子智能体为工具（使用 lambda 确保每次调用创建新实例）
            final AgentEntity finalSubDef = subDef;
            toolkit.registration()
                    .subAgent(() -> ReActAgent.builder()
                            .name(finalSubDef.getName())
                            .sysPrompt(finalSubDef.getSystemPrompt())
                            .model(defaultChatModel)
                            .memory(new InMemoryMemory())
                            .build(), config)
                    .apply();

            log.info("  ✅ 注册子智能体工具: {} -> {}", toolName, subDef.getName());
        }

        return ReActAgent.builder()
                .name(definition.getName())
                .sysPrompt(definition.getSystemPrompt())
                .model(defaultChatModel)
                .memory(new InMemoryMemory())
                .toolkit(toolkit)
                .build();
    }

    public AgentEntity getAgentDefinition(String agentId) {
        return agentDefinitions.get(agentId);
    }

    public List<AgentEntity> listAgentDefinitions() {
        return List.copyOf(agentDefinitions.values());
    }

    public void refreshAgent(String agentId) {
        AgentEntity agent = agentMapper.selectById(agentId);
        if (agent != null && Boolean.TRUE.equals(agent.getEnabled())) {
            agentDefinitions.put(agentId, agent);
        } else {
            agentDefinitions.remove(agentId);
        }
    }
}

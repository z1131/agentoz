package com.deepknow.agentoz.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.AgentConfigDTO;
import com.deepknow.agentoz.api.dto.AgentDefineRequest;
import com.deepknow.agentoz.api.dto.ConversationCreateRequest;
import com.deepknow.agentoz.api.service.AgentManagerService;
import com.deepknow.agentoz.infra.converter.api.ConfigApiAssembler;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.ConversationEntity;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.ConversationRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@DubboService
public class AgentManagerServiceImpl implements AgentManagerService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    @Override
    @Transactional
    public String createConversation(ConversationCreateRequest request) {
        String conversationId = UUID.randomUUID().toString();
        log.info("创建新会话: conversationId={}, userId={}", conversationId, request.getUserId());

        // 1. 创建会话实体
        ConversationEntity conversation = ConversationEntity.builder()
                .conversationId(conversationId)
                .userId(request.getUserId())
                .businessCode(request.getBusinessCode())
                .title(request.getTitle())
                .status("ACTIVE")
                // 初始化历史相关字段
                .historyContext("[]")  // 空数组
                .historyFormat("history_items_v1")
                .messageCount(0)
                .lastMessageContent(null)
                .lastMessageType(null)
                .lastMessageAt(null)
                .metadata(null)
                // 时间戳
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastActivityAt(LocalDateTime.now())
                .build();

        conversationRepository.insert(conversation);

        // 2. 创建 Primary Agent (必填)
        // 根据新规范，创建会话时必须同时创建主智能体
        if (request.getPrimaryAgent() != null) {
            log.info("初始化 Primary Agent: {}", request.getPrimaryAgent().getAgentName());
            request.getPrimaryAgent().setConversationId(conversationId);
            // 复用 defineAgent 逻辑创建实例
            defineAgent(request.getPrimaryAgent());
        } else {
            // 兼容性处理：如果未传 primaryAgent，仅记录警告（或抛出异常视业务严格程度而定）
            log.warn("createConversation 请求未包含 primaryAgent，创建了一个空会话。请尽快适配新 API。");
        }

        // 3. 创建 Sub Agents (可选)
        if (request.getSubAgents() != null && !request.getSubAgents().isEmpty()) {
            log.info("初始化 {} 个 Sub Agents", request.getSubAgents().size());
            for (AgentDefineRequest subAgentReq : request.getSubAgents()) {
                subAgentReq.setConversationId(conversationId);
                defineAgent(subAgentReq);
            }
        }

        return conversationId;
    }

    @Override
    @Transactional
    public String defineAgent(AgentDefineRequest request) {
        String agentId = UUID.randomUUID().toString();
        log.info("定义 Agent: agentId={}, conversationId={}, isPrimary={}, configId={}",
                agentId, request.getConversationId(), request.getIsPrimary(),
                request.getConfigId());

        // 1. 确定配置ID（复用或新建）
        String configId = request.getConfigId();
        if (configId == null || configId.isEmpty()) {
            // 方式2: 新建配置
            configId = createAgentConfig(request.getConfig());
            log.info("创建新配置: configId={}", configId);
        } else {
            // 方式1: 验证配置是否存在
            AgentConfigEntity existingConfig = agentConfigRepository.selectOne(
                    new LambdaQueryWrapper<AgentConfigEntity>().eq(AgentConfigEntity::getConfigId, configId)
            );
            if (existingConfig == null) {
                throw new IllegalArgumentException("配置不存在: configId=" + configId);
            }
            log.info("复用已有配置: configId={}", configId);
        }

        // 2. 创建Agent实例
        Integer priority = request.getPriority() != null ? request.getPriority() : 5;

        AgentEntity agent = AgentEntity.builder()
                .agentId(agentId)
                .conversationId(request.getConversationId())
                .configId(configId)
                .agentName(request.getAgentName())
                .isPrimary(request.getIsPrimary())
                .description(request.getDescription())
                .state("ACTIVE")
                // 初始化上下文相关字段
                .activeContext("[]")  // 空数组
                .contextFormat("history_items_v1")
                .stateDescription(null)
                .interactionCount(0)
                .lastInteractionType(null)
                .lastInteractionAt(null)
                // 其他字段
                .priority(priority)
                .metadata(null)
                // 时间戳
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .lastUsedAt(LocalDateTime.now())
                .build();

        agentRepository.insert(agent);
        log.info("Agent创建成功: agentId={}, configId={}", agentId, configId);

        return agentId;
    }

    @Override
    @Transactional
    public void removeAgent(String agentId) {
        log.info("删除 Agent: agentId={}", agentId);

        // 查询Agent信息
        AgentEntity agent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
        );

        if (agent == null) {
            log.warn("Agent不存在: agentId={}", agentId);
            return;
        }

        String configId = agent.getConfigId();

        // 删除Agent实例
        agentRepository.delete(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId));

        // 清理孤儿配置（如果配置不被其他Agent使用且不是模板配置）
        cleanupOrphanedConfig(configId);

        log.info("Agent删除成功: agentId={}, configId={}", agentId, configId);
    }

    /**
     * 清理不再使用的配置
     *
     * <p>如果配置满足以下条件之一，则不删除:</p>
     * <ul>
     *   <li>是模板配置 (isTemplate = true)</li>
     *   <li>还在被其他Agent使用</li>
     * </ul>
     *
     * @param configId 配置ID
     */
    private void cleanupOrphanedConfig(String configId) {
        // 1. 查询配置信息
        AgentConfigEntity config = agentConfigRepository.selectOne(
                new LambdaQueryWrapper<AgentConfigEntity>().eq(AgentConfigEntity::getConfigId, configId)
        );

        if (config == null) {
            log.warn("配置不存在: configId={}", configId);
            return;
        }

        // 2. 如果是模板配置,不删除
        if (Boolean.TRUE.equals(config.getIsTemplate())) {
            log.debug("跳过模板配置的清理: configId={}", configId);
            return;
        }

        // 3. 检查是否还有其他Agent使用此配置
        Long usageCount = agentRepository.selectCount(
                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConfigId, configId)
        );

        if (usageCount > 0) {
            log.debug("配置还在被其他Agent使用,不删除: configId={}, usageCount={}", configId, usageCount);
            return;
        }

        // 4. 删除孤儿配置
        agentConfigRepository.delete(new LambdaQueryWrapper<AgentConfigEntity>().eq(AgentConfigEntity::getConfigId, configId));
        log.info("清理孤儿配置成功: configId={}", configId);
    }

    @Override
    @Transactional
    public void endConversation(String conversationId) {
        log.info("结束会话: conversationId={}", conversationId);

        // 删除会话下的所有Agent
        agentRepository.delete(new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getConversationId, conversationId));

        // 删除会话
        conversationRepository.delete(new LambdaQueryWrapper<ConversationEntity>().eq(ConversationEntity::getConversationId, conversationId));

        log.info("会话结束成功: conversationId={}", conversationId);
    }

    /**
     * 创建Agent配置实体
     *
     * @param apiConfig API层的AgentConfigDTO
     * @return configId
     */
    private String createAgentConfig(AgentConfigDTO apiConfig) {
        String configId = "cfg-" + UUID.randomUUID().toString();

        log.info("========== createAgentConfig 开始 ==========");
        log.info("API Config: configName={}, llmModel={}", apiConfig.getConfigName(), apiConfig.getLlmModel());

        if (apiConfig.getMcpConfigJson() != null) {
            log.info("API Config 收到 mcpConfigJson (len={}): {}",
                    apiConfig.getMcpConfigJson().length(), apiConfig.getMcpConfigJson());
        } else {
            log.warn("API Config 的 mcpConfigJson 为空! ConfigName={}", apiConfig.getConfigName());
        }

        // 转换API层DTO到Server层VO (Assembler)
        var provider = ConfigApiAssembler.toProviderConfig(apiConfig.getProvider());
        var modelOverrides = ConfigApiAssembler.toModelOverrides(apiConfig.getModelOverrides());
        var sessionSource = ConfigApiAssembler.toSessionSource(apiConfig.getSessionSource());

        // 构建配置实体
        AgentConfigEntity configEntity = AgentConfigEntity.builder()
                .configId(configId)
                .configName(apiConfig.getConfigName())
                .description(apiConfig.getDescription())
                .tags(apiConfig.getTags())
                // 基础环境
                .provider(provider)  // 转换后的Server层DTO
                .llmModel(apiConfig.getLlmModel())
                .cwd(apiConfig.getCwd())
                // 策略配置
                .approvalPolicy(apiConfig.getApprovalPolicy())
                .sandboxPolicy(apiConfig.getSandboxPolicy())
                // 指令配置
                .developerInstructions(apiConfig.getDeveloperInstructions())
                .userInstructions(apiConfig.getUserInstructions())
                .baseInstructions(apiConfig.getBaseInstructions())
                // 推理配置
                .reasoningEffort(apiConfig.getReasoningEffort())
                .reasoningSummary(apiConfig.getReasoningSummary())
                .compactPrompt(apiConfig.getCompactPrompt())
                // 高级配置
                .modelOverrides(modelOverrides)  // 转换后的Server层DTO
                .mcpConfigJson(apiConfig.getMcpConfigJson()) // ✅ 直接使用 JSON 字符串
                .sessionSource(sessionSource)  // 转换后的Server层DTO
                // 元数据
                .isTemplate(false)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        log.info("即将保存 ConfigEntity: configId={}, mcpJsonLen={}",
                configId,
                configEntity.getMcpConfigJson() != null ? configEntity.getMcpConfigJson().length() : 0);

        agentConfigRepository.insert(configEntity);
        log.info("========== createAgentConfig 完成 ==========");

        return configId;
    }
}

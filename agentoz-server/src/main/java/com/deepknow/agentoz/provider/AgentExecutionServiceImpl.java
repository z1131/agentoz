package com.deepknow.agentoz.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.common.exception.AgentOzErrorCode;
import com.deepknow.agentoz.api.common.exception.AgentOzException;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.infra.converter.grpc.TaskResponseProtoConverter;
import com.deepknow.agentoz.infra.converter.grpc.HistoryProtoConverter;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.repo.AgentConfigRepository;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.util.StreamGuard;
import codex.agent.HistoryItem;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent 执行服务实现 (数据面)
 */
@Slf4j
@DubboService(protocol = "tri", timeout = 300000)
public class AgentExecutionServiceImpl implements AgentExecutionService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private CodexAgentClient codexAgentClient;

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String traceInfo = "ConvId=" + request.getConversationId();
        
        StreamGuard.run(responseObserver, () -> {
            String agentId = request.getAgentId();
            String conversationId = request.getConversationId();

            log.info(">>> 收到任务请求: {}", traceInfo);

            // 1. 自动寻找主智能体逻辑
            if (agentId == null || agentId.isEmpty()) {
                if (conversationId == null || conversationId.isEmpty()) {
                    throw new AgentOzException(AgentOzErrorCode.INVALID_PARAM, "agentId 和 conversationId 不能同时为空");
                }
                // 查询主智能体 (isPrimary = true)
                AgentEntity primaryAgent = agentRepository.selectOne(
                        new LambdaQueryWrapper<AgentEntity>()
                                .eq(AgentEntity::getConversationId, conversationId)
                                .eq(AgentEntity::getIsPrimary, true)
                );
                if (primaryAgent == null) {
                    throw new AgentOzException(AgentOzErrorCode.PRIMARY_AGENT_MISSING, conversationId);
                }
                agentId = primaryAgent.getAgentId();
                log.info("自动路由至主智能体: agentId={}", agentId);
            }

            // 2. 查询Agent实体
            final String finalAgentId = agentId;
            AgentEntity agent = agentRepository.selectOne(
                    new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, finalAgentId)
            );

            if (agent == null) {
                throw new AgentOzException(AgentOzErrorCode.AGENT_NOT_FOUND, finalAgentId);
            }

            // 3. 查询Agent配置
            AgentConfigEntity config = agentConfigRepository.selectOne(
                    new LambdaQueryWrapper<AgentConfigEntity>()
                            .eq(AgentConfigEntity::getConfigId, agent.getConfigId())
            );

            if (config == null) {
                throw new AgentOzException(AgentOzErrorCode.CONFIG_NOT_FOUND, agent.getConfigId());
            }

            // 4. 从AgentEntity的activeContext加载计算上下文
            List<HistoryItem> historyItems = parseActiveContext(agent.getActiveContext());

            log.info("准备调用Codex: agentId={}, model={}, historySize={}", 
                    finalAgentId, config.getLlmModel(), historyItems.size());

            // 5. 调用Codex-Agent计算节点 (使用 StreamGuard 包装 Observer)
            codexAgentClient.runTask(
                    agent.getConversationId(),
                    config,
                    historyItems,
                    request.getMessage(),
                    StreamGuard.wrapObserver(responseObserver, proto -> {
                        // 业务数据处理逻辑
                        log.debug("收到Codex帧: status={}, deltaLen={}, items={}", 
                                proto.getStatus(), proto.getTextDelta().length(), proto.getNewItemsJsonCount());
                        
                        TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);
                        responseObserver.onNext(dto);
                        
                    }, traceInfo)
            );
        }, traceInfo);
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        log.info("启动双向流式聊天（暂未实现）");
        return new StreamObserver<>() {
            @Override
            public void onNext(StreamChatRequest value) {}
            @Override
            public void onError(Throwable t) { responseObserver.onError(t); }
            @Override
            public void onCompleted() { responseObserver.onCompleted(); }
        };
    }

    private List<HistoryItem> parseActiveContext(String activeContextJson) {
        if (activeContextJson == null || activeContextJson.isEmpty() || "null".equals(activeContextJson)) {
            return List.of();
        }
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            List<com.deepknow.agentoz.dto.MessageDTO> messageDTOs = objectMapper.readValue(
                    activeContextJson,
                    new TypeReference<List<com.deepknow.agentoz.dto.MessageDTO>>() {}
            );
            return HistoryProtoConverter.toHistoryItemList(messageDTOs);
        } catch (Exception e) {
            log.warn("解析上下文失败(非致命，将使用空上下文): {}", e.getMessage());
            return List.of();
        }
    }
}

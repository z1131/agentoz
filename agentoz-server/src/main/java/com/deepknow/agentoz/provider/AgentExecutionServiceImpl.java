package com.deepknow.agentoz.provider;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.deepknow.agentoz.infra.adapter.grpc.HistoryItem;
import com.deepknow.agentoz.model.AgentConfigEntity;
import com.deepknow.agentoz.model.AgentEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行服务实现 (数据面)
 *
 * <h3>🔄 核心职责</h3>
 * <ul>
 *   <li>接收业务侧的任务请求</li>
 *   <li>查询Agent配置（双实体架构）</li>
 *   <li>调用Codex-Agent计算节点（Dubbo Triple + Reactor）</li>
 *   <li>流式返回任务执行过程</li>
 * </ul>
 *
 * <h3>📋 架构设计</h3>
 * <pre>
 * 业务层请求 → AgentExecutionService.executeTask()
 *     ↓
 * 查询 AgentEntity + AgentConfigEntity (双实体)
 *     ↓
 * 转换为 Proto格式 (EntityToProtoConverter)
 *     ↓
 * 调用 Codex-Agent (CodexAgentClient.runTask())
 *     ↓
 * Flux&lt;RunTaskResponse&gt; (Proto响应流)
 *     ↓
 * 转换为 DTO (ProtoToDtoConverter)
 *     ↓
 * Flux&lt;TaskResponse&gt; (返回给业务层)
 * </pre>
 *
 * @see com.deepknow.agentoz.api.service.AgentExecutionService
 * @see com.deepknow.agentoz.infra.client.CodexAgentClient
 */
@Slf4j
@DubboService(protocol = "tri")
public class AgentExecutionServiceImpl implements AgentExecutionService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private CodexAgentClient codexAgentClient;

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        String agentId = request.getAgentId();
        String conversationId = request.getConversationId();

        log.info("收到任务请求: agentId={}, conversationId={}, message={}", agentId, conversationId, request.getMessage());

        try {
            // 1. 自动寻找主智能体逻辑
            if (agentId == null || agentId.isEmpty()) {
                if (conversationId == null || conversationId.isEmpty()) {
                    throw new IllegalArgumentException("agentId 和 conversationId 不能同时为空");
                }
                // 查询主智能体 (isPrimary = true)
                AgentEntity primaryAgent = agentRepository.selectOne(
                        new LambdaQueryWrapper<AgentEntity>()
                                .eq(AgentEntity::getConversationId, conversationId)
                                .eq(AgentEntity::getIsPrimary, true)
                );
                if (primaryAgent == null) {
                    log.error("会话不存在主智能体: conversationId={}", conversationId);
                    throw new IllegalStateException("会话未定义主智能体: " + conversationId);
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
                log.error("Agent不存在: agentId={}", finalAgentId);
                throw new IllegalArgumentException("Agent不存在: " + finalAgentId);
            }

            // 3. 查询Agent配置
            AgentConfigEntity config = agentConfigRepository.selectOne(
                    new LambdaQueryWrapper<AgentConfigEntity>()
                            .eq(AgentConfigEntity::getConfigId, agent.getConfigId())
            );

            if (config == null) {
                log.error("Agent配置不存在: configId={}", agent.getConfigId());
                throw new IllegalArgumentException("Agent配置不存在: " + agent.getConfigId());
            }

            // 4. 从AgentEntity的activeContext加载计算上下文
            List<HistoryItem> historyItems = parseActiveContext(agent.getActiveContext());

            log.info("Agent配置加载完成: agentId={}, llmModel={}, conversationId={}, historySize={}",
                    finalAgentId, config.getLlmModel(), agent.getConversationId(), historyItems.size());

            // 5. 调用Codex-Agent计算节点 (Dubbo Observer 透传)
            codexAgentClient.runTask(
                    agent.getConversationId(),
                    config,
                    historyItems,
                    request.getMessage(),
                    new org.apache.dubbo.common.stream.StreamObserver<com.deepknow.agentoz.infra.adapter.grpc.RunTaskResponse>() {
                        @Override
                        public void onNext(com.deepknow.agentoz.infra.adapter.grpc.RunTaskResponse proto) {
                            try {
                                // Proto -> DTO
                                TaskResponse dto = TaskResponseProtoConverter.toTaskResponse(proto);
                                log.debug("收到任务响应: agentId={}, status={}", finalAgentId, dto.getStatus());
                                responseObserver.onNext(dto);
                            } catch (Exception e) {
                                log.error("响应转换或发送失败", e);
                                // 这里是否要onError取决于业务是否允许部分失败，通常建议onError
                                responseObserver.onError(e);
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.error("任务执行异常: agentId={}", finalAgentId, t);
                            // 构造错误响应或直接透传异常
                            TaskResponse errorResponse = new TaskResponse();
                            errorResponse.setStatus("ERROR");
                            errorResponse.setErrorMessage(t.getMessage());
                            responseObserver.onNext(errorResponse);
                            responseObserver.onCompleted();
                        }

                        @Override
                        public void onCompleted() {
                            log.info("任务执行完成: agentId={}", finalAgentId);
                            responseObserver.onCompleted();
                        }
                    }
            );

        } catch (Exception e) {
            log.error("任务请求处理失败: agentId={}", agentId, e);
            responseObserver.onError(e);
        }
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        // TODO: 实现双向流式调用（后续改造成 Flux<> Flux）
        log.info("启动双向流式聊天（暂未实现）");

        return new StreamObserver<StreamChatRequest>() {
            @Override
            public void onNext(StreamChatRequest value) {
                log.debug("收到流式请求: {}", value);
            }

            @Override
            public void onError(Throwable t) {
                log.error("流式请求异常", t);
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                log.info("流式请求完成");
                responseObserver.onCompleted();
            }
        };
    }

    /**
     * 解析Agent的活跃上下文（JSON格式）为Proto的HistoryItem列表
     *
     * <h3>📋 上下文说明</h3>
     * <ul>
     *   <li><b>activeContext</b>: Agent的计算上下文，传递给codex-agent的历史消息</li>
     *   <li><b>fullHistory</b>: Agent的完整历史记录（可能包含不活跃的历史）</li>
     * </ul>
     *
     * <h3>🔄 JSON格式示例</h3>
     * <pre>
     * [
     *   {
     *     "role": "user",
     *     "content": "帮我分析一下这个数据"
     *   },
     *   {
     *     "role": "assistant",
     *     "content": "好的，让我来看一下..."
     *   }
     * ]
     * </pre>
     *
     * @param activeContextJson JSON格式的活跃上下文
     * @return Proto的HistoryItem列表
     */
    private List<HistoryItem> parseActiveContext(String activeContextJson) {
        if (activeContextJson == null || activeContextJson.isEmpty() || "null".equals(activeContextJson)) {
            return List.of();
        }

        try {
            ObjectMapper objectMapper = new ObjectMapper();

            // 1. 解析JSON为MessageDTO列表
            List<com.deepknow.agentoz.dto.MessageDTO> messageDTOs = objectMapper.readValue(
                    activeContextJson,
                    new TypeReference<List<com.deepknow.agentoz.dto.MessageDTO>>() {}
            );

            // 2. 使用HistoryConverter转换为Proto列表
            List<HistoryItem> historyItems = HistoryProtoConverter.toHistoryItemList(messageDTOs);

            log.debug("解析活跃上下文成功: itemCount={}", historyItems.size());
            return historyItems;

        } catch (Exception e) {
            log.error("解析活跃上下文失败，返回空列表: activeContextJson={}", activeContextJson, e);
            return List.of();
        }
    }
}

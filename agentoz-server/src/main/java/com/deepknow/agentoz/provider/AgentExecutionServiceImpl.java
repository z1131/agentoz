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
@DubboService
public class AgentExecutionServiceImpl implements AgentExecutionService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private AgentConfigRepository agentConfigRepository;

    @Autowired
    private CodexAgentClient codexAgentClient;

    @Override
    public Flux<TaskResponse> executeTask(ExecuteTaskRequest request) {
        String agentId = request.getAgentId();

        log.info("开始执行任务: agentId={}, message={}", agentId, request.getMessage());

        // 1. 查询Agent实体
        AgentEntity agent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, agentId)
        );

        if (agent == null) {
            log.error("Agent不存在: agentId={}", agentId);
            return Flux.error(new IllegalArgumentException("Agent不存在: " + agentId));
        }

        // 2. 查询Agent配置
        AgentConfigEntity config = agentConfigRepository.selectOne(
                new LambdaQueryWrapper<AgentConfigEntity>()
                        .eq(AgentConfigEntity::getConfigId, agent.getConfigId())
        );

        if (config == null) {
            log.error("Agent配置不存在: configId={}", agent.getConfigId());
            return Flux.error(new IllegalArgumentException("Agent配置不存在: " + agent.getConfigId()));
        }

        // 3. 从AgentEntity的activeContext加载计算上下文（传给codex-agent的历史消息）
        //    注意: ConversationEntity.fullHistoryContext是给用户看的完整历史
        //         AgentEntity.activeContext是用于计算的活跃上下文
        List<HistoryItem> historyItems = parseActiveContext(agent.getActiveContext());

        log.info("Agent配置加载完成: agentId={}, llmModel={}, conversationId={}, historySize={}",
                agentId, config.getLlmModel(), agent.getConversationId(), historyItems.size());

        // 4. 调用Codex-Agent计算节点（返回Flux<RunTaskResponse>）
        Flux<com.deepknow.agentoz.infra.adapter.grpc.RunTaskResponse> protoFlux =
                codexAgentClient.runTask(
                        agent.getConversationId(),
                        config,
                        historyItems,
                        request.getMessage()
                );

        // 5. 转换Proto响应为DTO并返回
        return protoFlux
                .map(TaskResponseProtoConverter::toTaskResponse)  // Proto → DTO
                .doOnSubscribe(subscription -> log.info("开始订阅Codex-Agent响应流: agentId={}", agentId))
                .doOnNext(response -> log.debug("收到任务响应: agentId={}, status={}", agentId, response.getStatus()))
                .doOnComplete(() -> log.info("任务执行完成: agentId={}", agentId))
                .doOnError(error -> log.error("任务执行异常: agentId={}", agentId, error))
                .onErrorResume(error -> {
                    // 7. 错误处理：返回错误响应
                    TaskResponse errorResponse = new TaskResponse();
                    errorResponse.setStatus("ERROR");
                    errorResponse.setErrorMessage(error.getMessage());
                    return Flux.just(errorResponse);
                });
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

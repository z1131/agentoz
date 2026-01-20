package com.deepknow.agentoz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.*;
import com.deepknow.agentoz.api.enums.EventType;
import com.deepknow.agentoz.api.service.AgentService;
import com.deepknow.agentoz.entity.AgentEntity;
import com.deepknow.agentoz.manager.AgentManager;
import com.deepknow.agentoz.manager.StreamingHook;
import com.deepknow.agentoz.mapper.AgentMapper;
import com.deepknow.agentoz.session.RedisSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.session.Session;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.data.redis.core.StringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@DubboService
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentManager agentManager;
    private final AgentMapper agentMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 活跃会话的打断标记
     */
    private final Map<String, Boolean> interruptFlags = new ConcurrentHashMap<>();

    private Session getSession() {
        return new RedisSession(redisTemplate, objectMapper);
    }

    /**
     * 组装 Redis sessionId = agentId:conversationId
     */
    private String buildRedisSessionId(String agentId, String conversationId) {
        return agentId + ":" + conversationId;
    }

    @Override
    public Mono<SessionInitResponse> initSession(SessionInitRequest request) {
        return Mono.fromCallable(() -> {
            String sessionId = request.getSessionId();
            String primaryAgentId = request.getPrimaryAgentId();

            log.info("初始化会话: sessionId={}, primaryAgentId={}", sessionId, primaryAgentId);

            // 验证主智能体存在
            AgentEntity primaryAgent = agentManager.getAgentDefinition(primaryAgentId);
            if (primaryAgent == null) {
                return SessionInitResponse.builder()
                        .sessionId(sessionId)
                        .success(false)
                        .message("主智能体不存在: " + primaryAgentId)
                        .build();
            }

            // 收集可用的智能体ID
            List<String> availableAgentIds = new ArrayList<>();
            availableAgentIds.add(primaryAgentId);

            // 添加子智能体
            List<String> subAgentIds = primaryAgent.getSubAgentIds();
            if (subAgentIds != null) {
                availableAgentIds.addAll(subAgentIds);
            }

            // 预加载主智能体（触发子智能体工具注册）
            agentManager.getAgent(primaryAgentId);

            log.info("会话初始化成功: sessionId={}, agents={}", sessionId, availableAgentIds);

            return SessionInitResponse.builder()
                    .sessionId(sessionId)
                    .primaryAgentId(primaryAgentId)
                    .availableAgentIds(availableAgentIds)
                    .success(true)
                    .message("会话初始化成功")
                    .build();
        });
    }

    @Override
    public Mono<Boolean> interruptSession(String sessionId) {
        return Mono.fromCallable(() -> {
            log.info("打断会话: sessionId={}", sessionId);
            interruptFlags.put(sessionId, true);
            return true;
        });
    }

    /**
     * 检查会话是否被打断
     */
    private boolean isInterrupted(String sessionId) {
        return Boolean.TRUE.equals(interruptFlags.get(sessionId));
    }

    /**
     * 清除打断标记
     */
    private void clearInterruptFlag(String sessionId) {
        interruptFlags.remove(sessionId);
    }

    @Override
    public Flux<AgentChatResponse> streamChat(AgentChatRequest request) {
        String agentId = request.getAgentId();
        String conversationId = request.getSessionId();  // paper 传来的是 conversationId
        String redisSessionId = buildRedisSessionId(agentId, conversationId);

        return Mono.fromCallable(() -> {
                    if (agentId == null || agentId.isEmpty()) {
                        throw new IllegalArgumentException("agentId 不能为空");
                    }
                    if (conversationId == null || conversationId.isEmpty()) {
                        throw new IllegalArgumentException("conversationId 不能为空");
                    }
                    return agentManager.getAgentDefinition(agentId);
                })
                .flatMapMany(definition -> {
                    if (definition == null) {
                        return Flux.error(new IllegalArgumentException("Agent 不存在: " + agentId));
                    }

                    Sinks.Many<AgentChatResponse> sink = Sinks.many().unicast().onBackpressureBuffer();
                    Session session = getSession();

                    StreamingHook streamingHook = new StreamingHook(event -> {
                        AgentChatResponse response = switch (event.type()) {
                            case TEXT -> AgentChatResponse.builder()
                                    .sessionId(conversationId)
                                    .agentId(agentId)
                                    .content(event.content())
                                    .eventType(EventType.TEXT.name())
                                    .finished(false)
                                    .build();
                            case THINKING -> AgentChatResponse.builder()
                                    .sessionId(conversationId)
                                    .agentId(agentId)
                                    .content(event.content())
                                    .eventType(EventType.THINKING.name())
                                    .finished(false)
                                    .build();
                            case TOOL_CALL -> AgentChatResponse.builder()
                                    .sessionId(conversationId)
                                    .agentId(agentId)
                                    .content(event.content())
                                    .eventType(EventType.TOOL_CALL.name())
                                    .finished(false)
                                    .build();
                            case TOOL_RESULT -> AgentChatResponse.builder()
                                    .sessionId(conversationId)
                                    .agentId(agentId)
                                    .content(event.content())
                                    .eventType(EventType.TOOL_RESULT.name())
                                    .finished(false)
                                    .build();
                        };
                        sink.tryEmitNext(response);
                    });

                    // 获取 Agent 并加载 Session 状态（使用 redisSessionId）
                    ReActAgent agent = agentManager.getAgentWithSession(agentId, session, redisSessionId);
                    agent.getHooks().add(streamingHook);

                    Msg userMsg = Msg.builder()
                            .name("user")
                            .textContent(request.getMessage())
                            .build();

                    agent.call(userMsg)
                            .doOnSuccess(response -> {
                                // 保存 Session 状态（使用 redisSessionId）
                                agentManager.saveAgentSession(agentId, session, redisSessionId);
                                sink.tryEmitNext(AgentChatResponse.builder()
                                        .sessionId(conversationId)
                                        .agentId(agentId)
                                        .eventType(EventType.DONE.name())
                                        .finished(true)
                                        .build());
                                sink.tryEmitComplete();
                            })
                            .doOnError(e -> {
                                sink.tryEmitNext(AgentChatResponse.builder()
                                        .sessionId(conversationId)
                                        .agentId(agentId)
                                        .content("错误: " + e.getMessage())
                                        .eventType(EventType.ERROR.name())
                                        .finished(true)
                                        .build());
                                sink.tryEmitComplete();
                            })
                            .subscribe();

                    return sink.asFlux();
                })
                .onErrorResume(e -> {
                    log.error("流式对话失败", e);
                    return Flux.just(AgentChatResponse.builder()
                            .sessionId(conversationId)
                            .agentId(agentId)
                            .content("错误: " + e.getMessage())
                            .eventType(EventType.ERROR.name())
                            .finished(true)
                            .build());
                });
    }

    @Override
    public Mono<AgentChatResponse> chat(AgentChatRequest request) {
        String agentId = request.getAgentId();
        String conversationId = request.getSessionId();  // paper 传来的是 conversationId
        String redisSessionId = buildRedisSessionId(agentId, conversationId);

        return Mono.fromCallable(() -> {
                    if (agentId == null || agentId.isEmpty()) {
                        throw new IllegalArgumentException("agentId 不能为空");
                    }
                    if (conversationId == null || conversationId.isEmpty()) {
                        throw new IllegalArgumentException("conversationId 不能为空");
                    }
                    return agentManager.getAgentDefinition(agentId);
                })
                .flatMap(definition -> {
                    if (definition == null) {
                        return Mono.error(new IllegalArgumentException("Agent 不存在: " + agentId));
                    }

                    Session session = getSession();
                    ReActAgent agent = agentManager.getAgentWithSession(agentId, session, redisSessionId);

                    Msg userMsg = Msg.builder()
                            .name("user")
                            .textContent(request.getMessage())
                            .build();

                    return agent.call(userMsg)
                            .doOnSuccess(resp -> agentManager.saveAgentSession(agentId, session, redisSessionId))
                            .map(response -> AgentChatResponse.builder()
                                    .sessionId(conversationId)
                                    .agentId(agentId)
                                    .content(response.getTextContent())
                                    .eventType(EventType.TEXT.name())
                                    .finished(true)
                                    .build());
                })
                .onErrorResume(e -> {
                    log.error("对话失败", e);
                    return Mono.just(AgentChatResponse.builder()
                            .sessionId(conversationId)
                            .agentId(agentId)
                            .content("错误: " + e.getMessage())
                            .eventType(EventType.ERROR.name())
                            .finished(true)
                            .build());
                });
    }

    @Override
    public Mono<AgentDefinitionDTO> getAgent(String agentId) {
        return Mono.fromCallable(() -> {
            AgentEntity entity = agentManager.getAgentDefinition(agentId);
            if (entity == null) {
                entity = agentMapper.selectById(agentId);
            }
            return toDTO(entity);
        });
    }

    @Override
    public Mono<List<AgentDefinitionDTO>> listAgents() {
        return Mono.fromCallable(() -> {
            List<AgentEntity> entities = agentMapper.selectList(
                    new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getEnabled, true)
            );
            return entities.stream().map(this::toDTO).toList();
        });
    }

    @Override
    public Mono<AgentDefinitionDTO> createAgent(AgentDefinitionDTO definition) {
        return Mono.fromCallable(() -> {
            AgentEntity entity = toEntity(definition);
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            entity.setEnabled(true);
            agentMapper.insert(entity);
            agentManager.refreshAgent(entity.getId());
            return toDTO(entity);
        });
    }

    @Override
    public Mono<AgentDefinitionDTO> updateAgent(AgentDefinitionDTO definition) {
        return Mono.fromCallable(() -> {
            AgentEntity entity = toEntity(definition);
            entity.setUpdateTime(LocalDateTime.now());
            agentMapper.updateById(entity);
            agentManager.refreshAgent(entity.getId());
            return toDTO(entity);
        });
    }

    @Override
    public Mono<Boolean> deleteAgent(String agentId) {
        return Mono.fromCallable(() -> {
            int rows = agentMapper.deleteById(agentId);
            agentManager.refreshAgent(agentId);
            return rows > 0;
        });
    }

    private AgentDefinitionDTO toDTO(AgentEntity entity) {
        if (entity == null) return null;
        return AgentDefinitionDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .systemPrompt(entity.getSystemPrompt())
                .modelName(entity.getModelName())
                .tools(entity.getTools())
                .config(entity.getConfig())
                .build();
    }

    private AgentEntity toEntity(AgentDefinitionDTO dto) {
        return AgentEntity.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .systemPrompt(dto.getSystemPrompt())
                .modelName(dto.getModelName())
                .tools(dto.getTools())
                .config(dto.getConfig())
                .build();
    }
}

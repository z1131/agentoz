package com.deepknow.agentoz.service.impl;

import codex.agent.v1.Agent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.api.dto.ExecuteTaskRequest;
import com.deepknow.agentoz.api.dto.StreamChatRequest;
import com.deepknow.agentoz.api.dto.StreamChatResponse;
import com.deepknow.agentoz.api.dto.TaskResponse;
import com.deepknow.agentoz.api.service.AgentExecutionService;
import com.deepknow.agentoz.dto.codex.CodexSessionConfig;
import com.deepknow.agentoz.dto.codex.SandboxPolicy;
import com.deepknow.agentoz.infra.client.CodexAgentClient;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.model.AgentEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@DubboService
public class AgentExecutionServiceImpl implements AgentExecutionService {

    @Autowired
    private AgentRepository agentRepository;

    @Autowired
    private CodexAgentClient codexAgentClient;

    @Override
    public void executeTask(ExecuteTaskRequest request, StreamObserver<TaskResponse> responseObserver) {
        log.info("驱动任务执行: agentId={}", request.getAgentId());

        AgentEntity agent = agentRepository.selectOne(
                new LambdaQueryWrapper<AgentEntity>().eq(AgentEntity::getAgentId, request.getAgentId())
        );
        if (agent == null) {
            responseObserver.onError(new IllegalArgumentException("Agent 不存在: " + request.getAgentId()));
            return;
        }

        CodexSessionConfig codexConfig = CodexSessionConfig.builder()
                .provider(agent.getProvider())
                .model(agent.getModel())
                .developerInstructions(agent.getDeveloperInstructions())
                .userInstructions(agent.getUserInstructions())
                .mcpServers(agent.getMcpConfig())
                .cwd("/tmp/agentoz/" + agent.getSessionId())
                .sandboxPolicy(new SandboxPolicy.ReadOnly())
                .approvalPolicy("never")
                .sessionSource("api")
                .build();

        List<String> history = new ArrayList<>(); 

        codexAgentClient.runTask(agent.getSessionId(), codexConfig, history, request.getMessage())
                .subscribe(
                        rawResp -> {
                            TaskResponse taskResp = new TaskResponse();
                            taskResp.setStatus(rawResp.getStatus().name());
                            taskResp.setTextDelta(rawResp.getTextDelta());
                            taskResp.setFinalResponse(rawResp.getFinalResponse());
                            taskResp.setNewItemsJson(rawResp.getNewItemsJsonList());
                            
                            if (rawResp.hasUsage()) {
                                TaskResponse.Usage usage = new TaskResponse.Usage();
                                usage.promptTokens = rawResp.getUsage().getPromptTokens();
                                usage.totalTokens = rawResp.getUsage().getTotalTokens();
                                taskResp.setUsage(usage);
                            }
                            
                            responseObserver.onNext(taskResp);
                        },
                        err -> {
                            log.error("计算节点异常", err);
                            TaskResponse errResp = new TaskResponse();
                            errResp.setStatus("ERROR");
                            errResp.setErrorMessage(err.getMessage());
                            responseObserver.onNext(errResp);
                            responseObserver.onCompleted();
                        },
                        () -> {
                            log.info("任务流执行完毕: sessionId={}", agent.getSessionId());
                            responseObserver.onCompleted();
                        }
                );
    }

    @Override
    public StreamObserver<StreamChatRequest> streamInputExecuteTask(StreamObserver<StreamChatResponse> responseObserver) {
        return new StreamObserver<StreamChatRequest>() {
            @Override
            public void onNext(StreamChatRequest value) {}
            @Override
            public void onError(Throwable t) {}
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }
}

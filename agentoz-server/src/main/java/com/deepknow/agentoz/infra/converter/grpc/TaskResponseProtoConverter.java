package com.deepknow.agentoz.infra.converter.grpc;

import com.deepknow.agentoz.api.dto.TaskResponse;
import codex.agent.RunTaskResponse;
import codex.agent.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * 任务响应转换器
 */
@Slf4j
@Component
public class TaskResponseProtoConverter {

    /**
     * 转换 RunTaskResponse (Proto) → TaskResponse (DTO)
     *
     * @param protoResponse Codex-Agent返回的Proto响应
     * @return API层的TaskResponse DTO
     */
    public static TaskResponse toTaskResponse(RunTaskResponse protoResponse) {
        if (protoResponse == null) {
            return null;
        }

        TaskResponse dto = new TaskResponse();

        // 1. 基础状态
        if (protoResponse.getStatus() != null) {
            dto.setStatus(protoResponse.getStatus().name());
        }

        // 2. 增量内容
        if (!protoResponse.getTextDelta().isEmpty()) {
            dto.setTextDelta(protoResponse.getTextDelta());
        }
        // ReasoningDelta 暂未在 proto 中定义，先注释掉或假设它存在(如果proto已更新)
        // 经检查 agent.proto 中没有 reasoning_delta，只有 text_delta。
        // 如有需要请同步更新 agent.proto。这里先暂时移除对 reasoningDelta 的处理以免报错。
        /*
        if (!protoResponse.getReasoningDelta().isEmpty()) {
            dto.setReasoningDelta(protoResponse.getReasoningDelta());
        }
        */

        // 3. 最终回复
        if (!protoResponse.getFinalResponse().isEmpty()) {
            dto.setFinalResponse(protoResponse.getFinalResponse());
        }

        // 4. 结构化条目
        if (protoResponse.getNewItemsJsonCount() > 0) {
            dto.setNewItemsJson(protoResponse.getNewItemsJsonList());
        }

        // 5. Token使用统计
        if (protoResponse.hasUsage()) {
            TokenUsage protoUsage = protoResponse.getUsage();
            TaskResponse.Usage dtoUsage = new TaskResponse.Usage();
            dtoUsage.promptTokens = protoUsage.getPromptTokens();
            dtoUsage.completionTokens = String.valueOf(protoUsage.getCompletionTokens());
            dtoUsage.totalTokens = protoUsage.getTotalTokens();
            dto.setUsage(dtoUsage);
        }

        // 6. 错误信息
        if (!protoResponse.getErrorMessage().isEmpty()) {
            dto.setErrorMessage(protoResponse.getErrorMessage());
        }

        return dto;
    }
}

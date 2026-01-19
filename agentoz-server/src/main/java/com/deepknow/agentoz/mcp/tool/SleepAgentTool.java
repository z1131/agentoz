package com.deepknow.agentoz.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.deepknow.agentoz.enums.AsyncTaskStatus;
import com.deepknow.agentoz.infra.repo.AgentRepository;
import com.deepknow.agentoz.infra.repo.AsyncTaskRepository;
import com.deepknow.agentoz.model.AgentEntity;
import com.deepknow.agentoz.model.AsyncTaskEntity;
import com.deepknow.agentoz.service.RedisAgentTaskQueue;
import com.deepknow.agentoz.starter.annotation.AgentParam;
import com.deepknow.agentoz.starter.annotation.AgentTool;
import io.modelcontextprotocol.common.McpTransportContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 睡眠工具 - 让 Agent 暂时挂起，一段时间后自动唤醒
 *
 * <p>工作原理：
 * 1. 接收睡眠指令（秒）
 * 2. 计算唤醒时间
 * 3. 将任务加入 Redis 延迟队列
 * 4. 立即返回成功，CLI 进程随之销毁
 * 5. 时间到后，AgentOrchestrator 收到任务，拉起新进程恢复上下文
 * </p>
 */
@Slf4j
@Component
public class SleepAgentTool {

    @Autowired
    private RedisAgentTaskQueue redisAgentTaskQueue;

    @Autowired
    private AgentRepository agentRepository;
    
    @Autowired
    private AsyncTaskRepository asyncTaskRepository;

    /**
     * 让当前 Agent 睡眠一段时间
     *
     * @param ctx MCP 上下文
     * @param seconds 睡眠时长（秒）
     * @return 立即返回的消息，告知 Agent 已进入睡眠模式
     */
    @AgentTool(
        name = "sleep",
        description = "让当前 Agent 暂停执行一段时间（挂起进程）。时间到后，系统会自动唤醒 Agent 并恢复上下文。"
    )
    public String sleep(
        McpTransportContext ctx,
        @AgentParam(name = "seconds", value = "睡眠时长（单位：秒）") Integer seconds
    ) {
        // 1. 获取上下文信息
        String conversationId = getHeader(ctx, "X-Conversation-ID");
        String agentId = getHeader(ctx, "X-Agent-ID");

        if (conversationId == null || agentId == null) {
            return "执行失败: 无法获取上下文信息 (Missing X-Conversation-ID or X-Agent-ID)";
        }

        if (seconds == null || seconds <= 0) {
            return "执行失败: 睡眠时长必须大于 0 秒";
        }

        // 2. 计算时长（毫秒）
        long millis = seconds * 1000L;

        // 3. 验证 Agent 是否存在
        AgentEntity agent = agentRepository.selectOne(
            new LambdaQueryWrapper<AgentEntity>()
                .eq(AgentEntity::getAgentId, agentId)
        );
        String agentName = (agent != null) ? agent.getAgentName() : "Unknown Agent";

        // 4. 计算唤醒时间
        long wakeUpTime = System.currentTimeMillis() + millis;
        String wakeUpTimeStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(wakeUpTime), ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        log.info("💤 Agent 申请睡眠: agentId={}, seconds={}, wakeUpTime={}", agentId, seconds, wakeUpTimeStr);

        // 5. 创建唤醒任务
        // 唤醒时的提示词，Agent 醒来后会看到这句话作为 User Input
        String wakeUpPrompt = String.format("系统通知：睡眠结束，现在时间是 %s。请继续执行之前的任务。", wakeUpTimeStr);
        String taskId = UUID.randomUUID().toString();
        String parentTaskId = "main-" + conversationId; // 简单假设

        // 6. 存入数据库 (状态为 DELAYED)
        // 注意：AsyncTaskStatus 枚举可能没有 DELAYED，我们暂时用 QUEUED，或者在 Redis 侧标记延迟
        // 如果枚举不支持 DELAYED，我们先用 QUEUED，但在 Redis 里是 delayed_tasks
        AsyncTaskEntity taskEntity = AsyncTaskEntity.builder()
            .taskId(taskId)
            .agentId(agentId)
            .agentName(agentName)
            .conversationId(conversationId)
            .callerAgentId(agentId)
            .taskDescription(wakeUpPrompt)
            .priority("high")
            .parentTaskId(parentTaskId)
            .status(AsyncTaskStatus.QUEUED) // 暂时用 QUEUED
            .submitTime(LocalDateTime.now())
            .build();

        asyncTaskRepository.insert(taskEntity);

        // 7. 加入 Redis 延迟队列
        Map<String, String> meta = new HashMap<>();
        meta.put("conversationId", conversationId);
        
        redisAgentTaskQueue.enqueueDelayed(
            taskId,
            agentId,
            "high",
            millis,
            meta
        );

        // 8. 返回结果，结束当前回合
        return String.format("已成功设置睡眠。时长: %d 秒。将在 %s 自动唤醒。", seconds, wakeUpTimeStr);
    }

    private String getHeader(McpTransportContext ctx, String name) {
        if (ctx == null) return null;
        Object v = ctx.get(name);
        if (v == null) v = ctx.get(name.toLowerCase());
        return v != null ? v.toString() : null;
    }
}
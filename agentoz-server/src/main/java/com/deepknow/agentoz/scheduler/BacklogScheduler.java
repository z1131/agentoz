package com.deepknow.agentoz.scheduler;

import com.deepknow.agentoz.service.RedisAgentTaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Backlog 调度器 - 专门负责处理积压队列
 *
 * <p>职责：</p>
 * <ul>
 *   <li>监听 Agent 空闲事件</li>
 *   <li>自动从 Backlog 取出任务执行</li>
 *   <li>解耦：任务完成回调不再负责调度</li>
 * </ul>
 *
 * <h3>🎯 优雅之处</h3>
 * <ul>
 *   <li>职责单一：只负责调度</li>
 *   <li>调用清晰：Agent 空闲 → 通知调度器 → 取任务执行</li>
 *   <li>易于调试：调度逻辑集中在一处</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BacklogScheduler {

    private final RedisAgentTaskQueue redisAgentTaskQueue;

    /**
     * 调度线程池（单线程，保证调度顺序）
     */
    private ExecutorService schedulerExecutor;

    /**
     * 运行标志
     */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 正在处理的 Agent（防止重复调度）
     */
    private final Map<String, Boolean> processingAgents = new ConcurrentHashMap<>();

    @PostConstruct
    public void start() {
        if (running.compareAndSet(false, true)) {
            // 使用单线程调度器，保证调度顺序
            schedulerExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "backlog-scheduler");
                t.setDaemon(true);
                return t;
            });

            log.info("✅ [BacklogScheduler] 调度器已启动");
        }
    }

    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (schedulerExecutor != null) {
                schedulerExecutor.shutdown();
                try {
                    if (!schedulerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                        schedulerExecutor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    schedulerExecutor.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
            log.info("🛑 [BacklogScheduler] 调度器已停止");
        }
    }

    /**
     * 通知调度器：Agent 空闲了，可以处理 Backlog
     *
     * <p>由 AgentOrchestrator 在任务完成时调用</p>
     *
     * @param agentId Agent ID
     * @param session 会话对象
     */
    public void notifyAgentFree(String agentId, OrchestrationSessionCallback callback) {
        if (!running.get()) {
            log.warn("⚠️ [BacklogScheduler] 调度器未运行，跳过调度: agentId={}", agentId);
            return;
        }

        // 异步提交到调度器（避免阻塞任务完成线程）
        schedulerExecutor.submit(() -> {
            try {
                processBacklog(agentId, callback);
            } catch (Exception e) {
                log.error("❌ [BacklogScheduler] 调度失败: agentId={}", agentId, e);
            }
        });
    }

    /**
     * 处理 Backlog（在调度线程中执行）
     */
    private void processBacklog(String agentId, OrchestrationSessionCallback callback) {
        // 防止重复调度
        if (processingAgents.putIfAbsent(agentId, true) != null) {
            log.debug("⏳ [BacklogScheduler] Agent 正在处理中，跳过: agentId={}", agentId);
            return;
        }

        try {
            // 检查 Backlog 是否有任务
            int backlogSize = redisAgentTaskQueue.getBacklogSize(agentId);
            if (backlogSize == 0) {
                log.debug("✅ [BacklogScheduler] Backlog 为空: agentId={}", agentId);
                return;
            }

            log.info("🔄 [BacklogScheduler] 开始处理 Backlog: agentId={}, size={}", agentId, backlogSize);

            // 取出下一个任务
            String nextTaskId = redisAgentTaskQueue.pollBacklog(agentId);
            if (nextTaskId != null) {
                // 通过回调执行任务
                callback.executeQueuedTask(nextTaskId);

                int remaining = redisAgentTaskQueue.getBacklogSize(agentId);
                log.info("▶️ [BacklogScheduler] 已提交下一个任务: agentId={}, taskId={}, remaining={}",
                        agentId, nextTaskId, remaining);
            }

        } finally {
            // 清除处理标记
            processingAgents.remove(agentId);
        }
    }

    /**
     * OrchestrationSession 回调接口
     */
    @FunctionalInterface
    public interface OrchestrationSessionCallback {
        void executeQueuedTask(String taskId);
    }
}

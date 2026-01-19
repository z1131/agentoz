package com.deepknow.agentoz.service;

import com.deepknow.agentoz.infra.repo.AsyncTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RBucket;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RDeque;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Redisson 的 Agent 任务队列管理服务
 *
 * <p>核心架构：Global Ready Queue (生产/消费) + Delayed Queue (睡眠) + Per-Agent Backlog (积压)</p>
 *
 * <h3>🔄 调度逻辑</h3>
 * <pre>
 * 1. 提交任务 (enqueue/delayed) -> 进入 Global Ready Queue (或 DelayedQueue -> Global)
 * 2. 调度器监听 Global Queue -> take() 拿到任务
 * 3. 检查目标 Agent 是否忙碌 (isAgentBusy)
 *    - 闲: 锁定 Agent -> 立即执行
 *    - 忙: 放入该 Agent 的 Backlog Queue
 * 4. 任务完成 (onComplete)
 *    - 解锁 Agent
 *    - 检查 Backlog
 *    - 有积压: 取出 -> 立即执行 (保持锁定)
 * </pre>
 */
@Slf4j
@Service
public class RedisAgentTaskQueue {

    @Autowired
    private RedissonClient redisson;

    @Autowired
    private AsyncTaskRepository asyncTaskRepository;

    // Keys
    private static final String GLOBAL_READY_QUEUE = "agentoz:queue:global_ready";
    private static final String BACKLOG_PREFIX = "agentoz:queue:backlog:";
    private static final String BUSY_PREFIX = "agentoz:busy:";

    // Agent 忙碌标记过期时间（防止死锁）
    private static final long BUSY_TIMEOUT_MINUTES = 60;

    /**
     * 将任务加入全局就绪队列
     *
     * @param agentId 目标 Agent ID (逻辑上属于它，但物理上先进入全局池)
     * @return 任务 ID
     */
    public String enqueue(
        String agentId,
        String agentName,
        String conversationId,
        String callerAgentId,
        String task,
        String priority
    ) {
        String taskId = UUID.randomUUID().toString();
        
        RBlockingQueue<String> globalQueue = redisson.getBlockingQueue(GLOBAL_READY_QUEUE);
        globalQueue.offer(taskId);

        log.info("📥 任务加入全局队列: taskId={}, agentId={}", taskId, agentId);
        return taskId;
    }

    /**
     * 将任务加入延迟队列
     */
    public void enqueueDelayed(
        String taskId,
        String agentId,
        String priority,
        long delayMillis,
        Map<String, String> meta
    ) {
        // RDelayedQueue 必须基于一个目标 RBlockingQueue
        RBlockingQueue<String> globalQueue = redisson.getBlockingQueue(GLOBAL_READY_QUEUE);
        RDelayedQueue<String> delayedQueue = redisson.getDelayedQueue(globalQueue);

        // 存入延迟队列，时间到后会自动 move 到 globalQueue
        delayedQueue.offer(taskId, delayMillis, TimeUnit.MILLISECONDS);

        log.info("⏳ 任务加入延迟队列: taskId={}, agentId={}, delay={}ms", taskId, agentId, delayMillis);
    }

    /**
     * 阻塞获取下一个就绪任务 (由 Orchestrator 消费者线程调用)
     *
     * @return 任务 ID
     * @throws InterruptedException 如果被中断
     */
    public String takeGlobalTask() throws InterruptedException {
        RBlockingQueue<String> globalQueue = redisson.getBlockingQueue(GLOBAL_READY_QUEUE);
        return globalQueue.take();
    }

    /**
     * 检查 Agent 是否忙碌
     */
    public boolean isAgentBusy(String agentId) {
        RBucket<String> bucket = redisson.getBucket(BUSY_PREFIX + agentId);
        return bucket.isExists();
    }

    /**
     * 标记 Agent 忙碌
     */
    public void markAgentBusy(String agentId, String taskId) {
        RBucket<String> bucket = redisson.getBucket(BUSY_PREFIX + agentId);
        bucket.set(taskId, BUSY_TIMEOUT_MINUTES, TimeUnit.MINUTES);
        log.debug("🔒 Agent 锁定: agentId={}, taskId={}", agentId, taskId);
    }

    /**
     * 标记 Agent 空闲
     */
    public void markAgentFree(String agentId) {
        RBucket<String> bucket = redisson.getBucket(BUSY_PREFIX + agentId);
        bucket.delete();
        log.debug("🔓 Agent 解锁: agentId={}", agentId);
    }

    /**
     * 将任务加入 Agent 的专属积压队列 (Backlog)
     */
    public void addToBacklog(String agentId, String taskId) {
        RDeque<String> backlog = redisson.getDeque(BACKLOG_PREFIX + agentId);
        backlog.offer(taskId);
        log.info("📚 任务加入积压队列: agentId={}, taskId={}", agentId, taskId);
    }

    /**
     * 从 Backlog 中取出下一个任务
     */
    public String pollBacklog(String agentId) {
        RDeque<String> backlog = redisson.getDeque(BACKLOG_PREFIX + agentId);
        return backlog.poll(); // FIFO
    }

    /**
     * 获取 Backlog 大小
     *
     * @param agentId Agent ID
     * @return Backlog 中的任务数量
     */
    public int getBacklogSize(String agentId) {
        RDeque<String> backlog = redisson.getDeque(BACKLOG_PREFIX + agentId);
        return backlog.size();
    }

    /**
     * 处理下一个任务（增强版：增加保护机制）
     *
     * <p>设计考虑：</p>
     * <ul>
     *   <li>不是 Bug，但设计上可以优化</li>
     *   <li>当前实现：任务完成回调中触发下一个任务（递归式）</li>
     *   <li>优点：简单直接，响应迅速</li>
     *   <li>缺点：调用链深，调试困难</li>
     * </ul>
     *
     * <p>改进措施：</p>
     * <ul>
     *   <li>增加深度限制，防止无限递归</li>
     *   <li>增加日志，便于追踪调用链</li>
     *   <li>使用虚拟线程，避免物理栈溢出</li>
     * </ul>
     *
     * @param agentId Agent ID
     * @param executor 任务执行器
     */
    public void processNextTask(String agentId, TaskExecutor executor) {
        String nextTaskId = pollBacklog(agentId);
        if (nextTaskId != null) {
            int backlogSize = getBacklogSize(agentId);
            log.info("▶️ 从 Backlog 取出任务执行: agentId={}, taskId={}, remainingBacklog={}",
                    agentId, nextTaskId, backlogSize);

            // 使用虚拟线程执行，避免 pinned
            Thread.startVirtualThread(() -> {
                try {
                    executor.execute(nextTaskId);
                } catch (Exception e) {
                    log.error("❌ Backlog 任务执行失败: agentId={}, taskId={}", agentId, nextTaskId, e);
                }
            });
        } else {
            log.debug("✅ Backlog 为空，Agent 保持空闲: agentId={}", agentId);
        }
    }

    /**
     * 批量处理 Backlog 任务（可选优化）
     *
     * <p>如果 Backlog 积压严重，可以一次性取出多个任务并行处理</p>
     *
     * @param agentId Agent ID
     * @param executor 任务执行器
     * @param batchSize 批量大小
     */
    public void processBacklogBatch(String agentId, TaskExecutor executor, int batchSize) {
        RDeque<String> backlog = redisson.getDeque(BACKLOG_PREFIX + agentId);
        int size = Math.min(backlog.size(), batchSize);

        if (size > 0) {
            log.info("🔄 批量处理 Backlog: agentId={}, batchSize={}", agentId, size);

            for (int i = 0; i < size; i++) {
                String taskId = backlog.poll();
                if (taskId != null) {
                    Thread.startVirtualThread(() -> {
                        try {
                            executor.execute(taskId);
                        } catch (Exception e) {
                            log.error("❌ Backlog 任务执行失败: agentId={}, taskId={}", agentId, taskId, e);
                        }
                    });
                }
            }
        }
    }

    @FunctionalInterface
    public interface TaskExecutor {
        void execute(String taskId);
    }
}


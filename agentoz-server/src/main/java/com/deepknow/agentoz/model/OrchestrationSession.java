package com.deepknow.agentoz.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 编排会话 - 管理一次对话中的所有 Agent 调用
 *
 * <p>职责：</p>
 * <ul>
 *   <li>管理 SSE 连接（事件流）</li>
 *   <li>跟踪主任务和子任务的关系</li>
   *   <li>转发事件到正确的 Agent</li>
 *   <li>管理会话生命周期</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestrationSession {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationSession.class);

    /**
     * 会话唯一标识（对应 conversation_id）
     */
    private String sessionId;

    /**
     * 主任务 ID（根任务）
     */
    private String mainTaskId;

    /**
     * 当前活跃的 Agent ID
     */
    private String currentAgentId;

    /**
     * 会话状态
     */
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    /**
     * 会话创建时间
     */
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    /**
     * 最后更新时间
     */
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * 事件消费者（SSE 连接）
     */
    private Consumer<com.deepknow.agentoz.dto.InternalCodexEvent> eventConsumer;

    /**
     * 子任务映射：parent_task_id -> List<child_task_id>
     */
    @Builder.Default
    private Map<String, java.util.List<String>> taskTree = new ConcurrentHashMap<>();

    /**
     * 所有任务列表（按提交顺序）
     */
    @Builder.Default
    private java.util.List<String> allTaskIds = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 活跃子任务计数（用于判断是否可以关闭 SSE）
     */
    @Builder.Default
    private java.util.concurrent.atomic.AtomicInteger activeSubTaskCount = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 打断标志：用户主动取消任务
     */
    private volatile boolean cancelled = false;

    /**
     * 打断原因
     */
    private String cancelReason;

    /**
     * 事件订阅者列表（支持多个 SSE 连接同时订阅）
     */
    @Builder.Default
    private java.util.List<Consumer<com.deepknow.agentoz.dto.InternalCodexEvent>> subscribers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 会话状态枚举
     */
    public enum SessionStatus {
        ACTIVE,      // 活跃：正在执行
        IDLE,        // 空闲：等待输入
        COMPLETED,   // 完成：任务全部完成
        FAILED,      // 失败：执行出错
        CANCELLED    // 已取消：用户主动取消
    }

    // ========== 业务方法 ==========

    /**
     * 添加子任务到调用树
     */
    public void addChildTask(String parentTaskId, String childTaskId) {
        taskTree.computeIfAbsent(parentTaskId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                .add(childTaskId);
        allTaskIds.add(childTaskId);
        activeSubTaskCount.incrementAndGet();
        updatedAt = LocalDateTime.now();
    }

    /**
     * 标记子任务完成
     */
    public void completeSubTask(String taskId) {
        int count = activeSubTaskCount.decrementAndGet();
        updatedAt = LocalDateTime.now();

        if (count <= 0) {
            // 所有子任务完成
            status = SessionStatus.COMPLETED;
        }
    }

    /**
     * 发送事件到所有订阅者
     */
    public void sendEvent(com.deepknow.agentoz.dto.InternalCodexEvent event) {
        // 确保 subscribers 列表已初始化
        if (subscribers == null) {
            log.warn("🔧 [OrchestrationSession] subscribers 列表为 null，跳过发送: sessionId={}", sessionId);
            return;
        }

        // 发送给所有订阅者
        subscribers.forEach(subscriber -> {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                // 订阅者断开，自动移除
                subscribers.remove(subscriber);
            }
        });

        // 兼容旧的 eventConsumer（如果存在）
        if (eventConsumer != null) {
            try {
                eventConsumer.accept(event);
            } catch (Exception e) {
                // 忽略异常
            }
        }
    }

    /**
     * 检查是否可以关闭会话
     */
    public boolean canClose() {
        return status == SessionStatus.COMPLETED ||
               status == SessionStatus.FAILED ||
               (activeSubTaskCount.get() <= 0 && status == SessionStatus.IDLE);
    }

    /**
     * 增加活跃子任务计数
     */
    public void incrementActiveTasks() {
        activeSubTaskCount.incrementAndGet();
    }

    /**
     * 减少活跃子任务计数
     */
    public void decrementActiveTasks() {
        activeSubTaskCount.decrementAndGet();
    }

    /**
     * 获取活跃子任务数量
     */
    public int getActiveTaskCount() {
        return activeSubTaskCount.get();
    }

    /**
     * 设置事件消费者
     */
    public void setEventConsumer(Consumer<com.deepknow.agentoz.dto.InternalCodexEvent> consumer) {
        this.eventConsumer = consumer;
    }

    /**
     * 会话是否活跃
     */
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    // ========== 打断相关方法 ==========

    /**
     * 取消任务（用户主动取消）
     */
    public void cancel(String reason) {
        this.cancelled = true;
        this.cancelReason = reason;
        this.status = SessionStatus.CANCELLED;
        updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否应该停止任务（仅检查用户主动取消）
     */
    public boolean shouldStop() {
        return cancelled;
    }

    /**
     * 检查会话是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }

    /**
     * 获取取消原因
     */
    public String getCancelReason() {
        return cancelReason;
    }

    // ========== 订阅者管理 ==========

    /**
     * 订阅事件流
     *
     * @param subscriber 事件消费者
     */
    public void subscribe(Consumer<com.deepknow.agentoz.dto.InternalCodexEvent> subscriber) {
        // 确保 subscribers 列表已初始化
        if (subscribers == null) {
            log.warn("🔧 [OrchestrationSession] subscribers 列表未初始化，重新初始化: sessionId={}", sessionId);
            subscribers = new java.util.concurrent.CopyOnWriteArrayList<>();
        }
        subscribers.add(subscriber);
        log.info("📡 [OrchestrationSession] 新订阅者: sessionId={}, subscribers={}",
                sessionId, subscribers.size());
    }

    /**
     * 取消订阅
     *
     * @param subscriber 事件消费者
     */
    public void unsubscribe(Consumer<com.deepknow.agentoz.dto.InternalCodexEvent> subscriber) {
        subscribers.remove(subscriber);
        log.info("🔌 [OrchestrationSession] 取消订阅: sessionId={}, subscribers={}",
                sessionId, subscribers.size());
    }

    /**
     * 获取当前订阅者数量
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }
}

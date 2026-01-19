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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 *
 * <h3>🔒 线程安全改进</h3>
 * <ul>
 *   <li>使用单线程事件调度器，确保 StreamObserver.onNext() 串行调用</li>
 *   <li>虚拟线程产生的事件 → 调度器队列 → 单线程发送</li>
 *   <li>避免 StreamObserver 并发写入导致的异常</li>
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
     * 子任务映射：parent_task_id -> List<child-task_id>
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
     * 流关闭标志（防止 onCompleted 多次调用）
     *
     * <p>为什么需要？</p>
     * <ul>
     *   <li>多个任务可能同时完成（并发）</li>
     *   <li>竞态条件可能导致 onCompleted 被多次调用</li>
     *   <li>Dubbo/gRPC 的 StreamObserver.onCompleted() 只能调用一次</li>
     * </ul>
     */
    @Builder.Default
    private java.util.concurrent.atomic.AtomicBoolean streamClosed = new java.util.concurrent.atomic.AtomicBoolean(false);

    /**
     * 事件订阅者列表（支持多个 SSE 连接同时订阅）
     */
    @Builder.Default
    private java.util.List<Consumer<com.deepknow.agentoz.dto.InternalCodexEvent>> subscribers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * 事件调度器（单线程执行器，确保 StreamObserver.onNext() 串行调用）
     *
     * <p>为什么需要单线程调度器？</p>
     * <ul>
     *   <li>Dubbo/gRPC 的 StreamObserver 不是线程安全的</li>
     *   <li>虚拟线程可能并发产生事件</li>
     *   <li>必须保证 onNext() 串行调用，否则会导致：</li>
     *   <ul>
     *     <li>消息乱序</li>
     *     <li>IllegalStateException: call already half-closed</li>
     *     <li>数据帧损坏</li>
     *   </ul>
     * </ul>
     *
     * <p>为什么不使用 Executors.newSingleThreadExecutor()？</p>
     * <ul>
     *   <li>阿里巴巴开发手册禁止使用 Executors 工具方法</li>
     *   <li>无界队列可能导致 OOM</li>
     *   <li>需要显式配置拒绝策略和队列大小</li>
     * </ul>
     */
    @Builder.Default
    private transient ExecutorService eventDispatcher = new java.util.concurrent.ThreadPoolExecutor(
            1,                                      // corePoolSize: 核心线程数
            1,                                      // maximumPoolSize: 最大线程数
            0L,                                     // keepAliveTime: 空闲线程存活时间
            java.util.concurrent.TimeUnit.MILLISECONDS,
            new java.util.concurrent.ArrayBlockingQueue<>(1000), // 有界队列，防止 OOM
            r -> {                                  // 自定义线程工厂
                Thread t = new Thread(r, "event-dispatcher-" + System.currentTimeMillis());
                t.setDaemon(true);                 // 守护线程，JVM 退出时不阻塞
                t.setPriority(Thread.NORM_PRIORITY); // 正常优先级
                return t;
            },
            new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略：调用者运行
    );

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
     * 发送事件到所有订阅者（线程安全版本）
     *
     * <p>实现细节：</p>
     * <ul>
     *   <li>调用线程：虚拟线程（并发）</li>
     *   <li>调度器：单线程执行器</li>
     *   <li>实际发送：调度器线程（串行）</li>
     * </ul>
     */
    public void sendEvent(com.deepknow.agentoz.dto.InternalCodexEvent event) {
        // 确保 subscribers 列表已初始化
        if (subscribers == null) {
            log.warn("🔧 [OrchestrationSession] subscribers 列表为 null，跳过发送: sessionId={}", sessionId);
            return;
        }

        // 异步提交到单线程调度器（避免阻塞虚拟线程）
        eventDispatcher.submit(() -> {
            try {
                // 发送给所有订阅者（在调度器线程中串行执行）
                subscribers.forEach(subscriber -> {
                    try {
                        subscriber.accept(event);
                    } catch (Exception e) {
                        // 订阅者断开，自动移除
                        log.warn("🔌 [OrchestrationSession] 订阅者异常，移除: sessionId={}, error={}",
                                sessionId, e.getMessage());
                        subscribers.remove(subscriber);
                    }
                });

                // 兼容旧的 eventConsumer（如果存在）
                if (eventConsumer != null) {
                    try {
                        eventConsumer.accept(event);
                    } catch (Exception e) {
                        log.debug("[OrchestrationSession] eventConsumer 异常: sessionId={}, error={}",
                                sessionId, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("[OrchestrationSession] 事件发送失败: sessionId={}, eventType={}",
                        sessionId, event.getEventType(), e);
            }
        });
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
     * <p>支持多订阅者（多页面场景）：
     * <ul>
     *   <li>页面A：SSE 连接活跃</li>
     *   <li>页面B：刷新后重连，创建新的 SSE 连接</li>
     *   <li>页面C：新开窗口，创建新的 SSE 连接</li>
     * </ul>
     * 所有订阅者都会收到相同的事件（广播模式）</p>
     *
     * @param subscriber 事件消费者
     */
    public void subscribe(Consumer<com.deepknow.agentoz.dto.InternalCodexEvent> subscriber) {
        // 确保 subscribers 列表已初始化
        if (subscribers == null) {
            log.warn("🔧 [OrchestrationSession] subscribers 列表未初始化，重新初始化: sessionId={}", sessionId);
            subscribers = new java.util.concurrent.CopyOnWriteArrayList<>();
        }

        // 不清空旧订阅者！直接添加新的（支持多页面）
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

    /**
     * 尝试关闭流（线程安全，只执行一次）
     *
     * <p>使用 CAS (Compare-And-Swap) 确保即使在多线程并发调用的情况下，
     * onComplete 回调也只会执行一次</p>
     *
     * @param onComplete 完成回调
     * @return true 如果成功关闭（第一次调用），false 如果已经关闭
     */
    public boolean tryCloseStream(Runnable onComplete) {
        // CAS 操作：只有当 streamClosed 为 false 时才设置为 true
        if (streamClosed.compareAndSet(false, true)) {
            log.info("🔒 [OrchestrationSession] 流关闭锁获取成功: sessionId={}", sessionId);
            try {
                if (onComplete != null) {
                    onComplete.run();
                }
                return true;
            } catch (Exception e) {
                log.error("[OrchestrationSession] onComplete 回调执行失败: sessionId={}", sessionId, e);
                return false;
            }
        } else {
            log.debug("🔒 [OrchestrationSession] 流已经关闭，跳过重复调用: sessionId={}", sessionId);
            return false;
        }
    }

    /**
     * 检查流是否已关闭
     */
    public boolean isStreamClosed() {
        return streamClosed.get();
    }

    /**
     * 关闭会话（释放资源）
     *
     * <p>注意：必须在会话不再使用时调用，否则会泄漏线程</p>
     */
    public void close() {
        if (eventDispatcher != null && !eventDispatcher.isShutdown()) {
            log.info("🔒 [OrchestrationSession] 关闭事件调度器: sessionId={}", sessionId);
            eventDispatcher.shutdown();
            try {
                if (!eventDispatcher.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("⚠️ [OrchestrationSession] 事件调度器未能在5秒内关闭，强制关闭: sessionId={}", sessionId);
                    eventDispatcher.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("[OrchestrationSession] 关闭事件调度器被中断: sessionId={}", sessionId, e);
                eventDispatcher.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

package com.deepknow.agentoz.manager;

import com.deepknow.agentoz.dto.A2AContext;
import com.deepknow.agentoz.dto.InternalCodexEvent;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A2A 任务注册表
 * 
 * 职责：
 * 1. 维护正在执行的任务列表 (TaskID -> TaskRecord)
 * 2. 关联任务与其输出通道 (EventConsumer)
 * 3. 支持子任务通过父 ID 自动寻找根节点的输出通道
 */
@Slf4j
@Component
public class A2ATaskRegistry {

    @Data
    @Builder
    public static class TaskRecord {
        private String taskId;
        private String conversationId;
        private A2AContext a2aContext;
        private Consumer<InternalCodexEvent> eventConsumer;
        private long startTime;
    }

    // 存储任务记录 (Key: TaskID)
    private final Map<String, TaskRecord> activeTasks = new ConcurrentHashMap<>();
    
    // 建立 ParentTaskID -> RootTaskID 的映射，方便快速查找
    private final Map<String, String> parentToRootMap = new ConcurrentHashMap<>();

    /**
     * 注册任务
     */
    public void registerTask(TaskRecord record) {
        if (record == null || record.getTaskId() == null) return;
        
        activeTasks.put(record.getTaskId(), record);
        
        // 如果是子任务，建立溯源关系
        if (record.getA2aContext() != null && record.getA2aContext().getParentTaskId() != null) {
            String parentId = record.getA2aContext().getParentTaskId();
            // 查找父任务的根
            String rootId = parentToRootMap.getOrDefault(parentId, parentId);
            parentToRootMap.put(record.getTaskId(), rootId);
        }
        
        log.info("[A2ATaskRegistry] ✓ 注册任务: taskId={}, convId={}, depth={}", 
                record.getTaskId(), record.getConversationId(), 
                record.getA2aContext() != null ? record.getA2aContext().getDepth() : 0);
    }

    /**
     * 注销任务
     */
    public void unregisterTask(String taskId) {
        if (taskId == null) return;
        activeTasks.remove(taskId);
        parentToRootMap.remove(taskId);
        log.debug("[A2ATaskRegistry] 注销任务: taskId={}", taskId);
    }

    /**
     * 根据任务上下文寻找有效的输出通道
     * 逻辑：
     * 1. 如果当前任务有 Consumer，直接返回
     * 2. 如果没有，向上追溯父任务，直到找到根任务的 Consumer
     */
    public Consumer<InternalCodexEvent> findConsumer(String taskId) {
        if (taskId == null) return null;

        // 1. 直接查找
        TaskRecord record = activeTasks.get(taskId);
        if (record != null && record.getEventConsumer() != null) {
            return record.getEventConsumer();
        }

        // 2. 溯源查找根任务的 Consumer
        String rootId = parentToRootMap.get(taskId);
        if (rootId != null) {
            TaskRecord rootRecord = activeTasks.get(rootId);
            if (rootRecord != null) {
                return rootRecord.getEventConsumer();
            }
        }

        return null;
    }

    /**
     * 向任务发送事件
     */
    public void sendEvent(String taskId, InternalCodexEvent event) {
        Consumer<InternalCodexEvent> consumer = findConsumer(taskId);
        if (consumer != null) {
            try {
                consumer.accept(event);
            } catch (Exception e) {
                log.warn("[A2ATaskRegistry] 发送事件失败: taskId={}, error={}", taskId, e.getMessage());
            }
        }
    }
}

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
        private Consumer<String> onTaskCompleted;
        private long startTime;
    }

    private final Map<String, TaskRecord> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, String> parentToRootMap = new ConcurrentHashMap<>();

    public void registerTask(TaskRecord record) {
        if (record == null || record.getTaskId() == null) return;
        activeTasks.put(record.getTaskId(), record);
        if (record.getA2aContext() != null && record.getA2aContext().getParentTaskId() != null) {
            String parentId = record.getA2aContext().getParentTaskId();
            String rootId = parentToRootMap.getOrDefault(parentId, parentId);
            parentToRootMap.put(record.getTaskId(), rootId);
        }
        log.info("[A2ATaskRegistry] Register: taskId={}, depth={}", record.getTaskId(), record.getA2aContext() != null ? record.getA2aContext().getDepth() : 0);
    }

    public void unregisterTask(String taskId) {
        if (taskId == null) return;
        activeTasks.remove(taskId);
        parentToRootMap.remove(taskId);
    }

    public void completeTask(String taskId, String result) {
        TaskRecord record = activeTasks.get(taskId);
        if (record != null && record.getOnTaskCompleted() != null) {
            record.getOnTaskCompleted().accept(result);
        }
        unregisterTask(taskId);
    }

    public void sendEvent(String taskId, InternalCodexEvent event) {
        Consumer<InternalCodexEvent> consumer = findConsumer(taskId);
        if (consumer != null) consumer.accept(event);
    }

    private Consumer<InternalCodexEvent> findConsumer(String taskId) {
        TaskRecord record = activeTasks.get(taskId);
        if (record != null && record.getEventConsumer() != null) return record.getEventConsumer();
        String rootId = parentToRootMap.get(taskId);
        if (rootId != null) {
            TaskRecord root = activeTasks.get(rootId);
            if (root != null) return root.getEventConsumer();
        }
        return null;
    }
}
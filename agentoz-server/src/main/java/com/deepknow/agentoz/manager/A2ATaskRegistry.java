package com.deepknow.agentoz.manager;

import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
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
        private Task task;
        private String conversationId;
        private Consumer<InternalCodexEvent> eventConsumer;
        private Consumer<Task> onTaskTerminal;
        private long startTime;
    }

    private final Map<String, TaskRecord> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, String> parentToRootMap = new ConcurrentHashMap<>();

    public void registerTask(TaskRecord record) {
        if (record == null || record.getTask() == null) return;
        String taskId = record.getTask().id(); 
        activeTasks.put(taskId, record);
        parentToRootMap.put(taskId, record.getConversationId());
        log.info("[A2ATaskRegistry] Task Registered: id={}, state={}", taskId, record.getTask().status().state());
    }

    public void updateTask(String taskId, Task updatedTask) {
        TaskRecord record = activeTasks.get(taskId);
        if (record == null) return;
        record.setTask(updatedTask);
        TaskState state = updatedTask.status().state();
        if (state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELED) {
            log.info("[A2ATaskRegistry] Terminal state: {} -> {}", taskId, state);
            if (record.getOnTaskTerminal() != null) record.getOnTaskTerminal().accept(updatedTask);
            activeTasks.remove(taskId);
            parentToRootMap.remove(taskId);
        }
    }

    public void unregisterTask(String taskId) {
        if (taskId == null) return;
        activeTasks.remove(taskId);
        parentToRootMap.remove(taskId);
    }

    public void sendEvent(String taskId, InternalCodexEvent event) {
        Consumer<InternalCodexEvent> consumer = findRootConsumer(taskId);
        if (consumer != null) consumer.accept(event);
    }

    private Consumer<InternalCodexEvent> findRootConsumer(String taskId) {
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
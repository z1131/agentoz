package com.deepknow.agentoz.infra.config;

import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.Task;
import io.a2a.spec.TaskState;
import io.a2a.spec.TaskStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Configuration
@Slf4j
public class A2AConfig {

    @Bean
    @Primary
    public TaskStore taskStore() {
        return new ObservableTaskStore();
    }

    /**
     * 正统 A2A 存储扩展：支持异步唤醒
     */
    public interface A2AObservableStore extends TaskStore {
        void addTerminalListener(String taskId, Consumer<Task> listener);
    }

    public static class ObservableTaskStore extends InMemoryTaskStore implements A2AObservableStore {
        private final Map<String, Consumer<Task>> terminalListeners = new ConcurrentHashMap<>();
        private final Map<String, Task> completedTasksCache = new ConcurrentHashMap<>();

        @Override
        public void addTerminalListener(String taskId, Consumer<Task> listener) {
            // ⭐ 核心：防竞争。如果任务已经提前完成了，直接触发
            Task alreadyDone = completedTasksCache.get(taskId);
            if (alreadyDone != null) {
                log.info("[A2A] Task {} was already completed, triggering immediate callback.", taskId);
                listener.accept(alreadyDone);
                completedTasksCache.remove(taskId);
                return;
            }
            terminalListeners.put(taskId, listener);
        }

        @Override
        public void save(Task task) {
            super.save(task);
            if (task == null || task.getId() == null) return;

            TaskStatus status = task.getStatus();
            if (status == null) return;

            TaskState state = status.state();
            if (state == TaskState.COMPLETED || state == TaskState.FAILED || state == TaskState.CANCELED) {
                Consumer<Task> listener = terminalListeners.remove(task.getId());
                if (listener != null) {
                    log.info("[A2A] Notifying listener for terminal task: {}", task.getId());
                    listener.accept(task);
                } else {
                    // 如果监听器还没来，先存入缓存
                    completedTasksCache.put(task.getId(), task);
                }
            }
        }
    }
}
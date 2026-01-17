package com.deepknow.agentoz.infra.config;

import io.a2a.server.tasks.InMemoryTaskStore;
import io.a2a.server.tasks.TaskStore;
import io.a2a.spec.Task;
import io.a2a.spec.TaskStatus;
import io.a2a.spec.TaskState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

@Configuration
@Slf4j
public class A2AConfig {

    /**
     * 注册正统 A2A 任务存储
     */
    @Bean
    public TaskStore taskStore() {
        return new ObservableTaskStore();
    }

    /**
     * 扩展官方存储，增加回调监听能力
     */
    public static class ObservableTaskStore extends InMemoryTaskStore {
        private final Map<String, Consumer<Task>> terminalListeners = new ConcurrentHashMap<>();

        public void addTerminalListener(String taskId, Consumer<Task> listener) {
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
                    log.info("[A2A] Task {} terminal state reached, notifying listener.", task.getId());
                    listener.accept(task);
                }
            }
        }
    }
}

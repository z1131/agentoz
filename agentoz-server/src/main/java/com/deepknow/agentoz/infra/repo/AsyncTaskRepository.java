package com.deepknow.agentoz.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agentoz.enums.AsyncTaskStatus;
import com.deepknow.agentoz.model.AsyncTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 异步任务数据访问层
 *
 * @see AsyncTaskEntity
 */
@Mapper
public interface AsyncTaskRepository extends BaseMapper<AsyncTaskEntity> {

    /**
     * 根据 task_id 查询任务
     *
     * @param taskId 任务 ID
     * @return 任务实体，如果不存在则返回 null
     */
    @Select("SELECT * FROM async_tasks WHERE task_id = #{taskId}")
    AsyncTaskEntity findByTaskId(@Param("taskId") String taskId);

    /**
     * 查询指定 Agent 的所有正在运行的任务
     *
     * @param agentId Agent ID
     * @return 正在运行的任务列表
     */
    @Select("SELECT * FROM async_tasks WHERE agent_id = #{agentId} AND status IN ('SUBMITTED', 'RUNNING')")
    List<AsyncTaskEntity> findRunningTasksByAgentId(@Param("agentId") String agentId);

    /**
     * 查询指定会话的所有任务
     *
     * @param conversationId 会话 ID
     * @return 任务列表
     */
    @Select("SELECT * FROM async_tasks WHERE conversation_id = #{conversationId} ORDER BY submit_time DESC")
    List<AsyncTaskEntity> findByConversationId(@Param("conversationId") String conversationId);

    /**
     * 查询指定状态的未完成超时任务
     * （用于清理超时任务）
     *
     * @param status 任务状态
     * @param timeoutMinutes 超时时间（分钟）
     * @return 超时任务列表
     */
    @Select("SELECT * FROM async_tasks WHERE status = #{status} " +
            "AND submit_time < #{timeoutThreshold} " +
            "ORDER BY submit_time ASC")
    List<AsyncTaskEntity> findTimeoutTasks(
        @Param("status") AsyncTaskStatus status,
        @Param("timeoutThreshold") LocalDateTime timeoutThreshold
    );

    /**
     * 统计指定 Agent 的队列中任务数量
     *
     * @param agentId Agent ID
     * @return 队列中的任务数量
     */
    @Select("SELECT COUNT(*) FROM async_tasks WHERE agent_id = #{agentId} AND status = 'QUEUED'")
    int countQueuedTasksByAgentId(@Param("agentId") String agentId);
}

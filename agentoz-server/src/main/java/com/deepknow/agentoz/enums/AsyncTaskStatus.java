package com.deepknow.agentoz.enums;

/**
 * 异步任务状态枚举
 *
 * <p>用于 AsyncCallAgent 的任务生命周期管理</p>
 *
 * @see com.deepknow.agentoz.mcp.tool.AsyncCallAgentTool
 */
public enum AsyncTaskStatus {
    /**
     * 已提交 - 任务已创建，等待执行
     */
    SUBMITTED,

    /**
     * 队列中 - Agent 正忙，任务在队列中等待
     */
    QUEUED,

    /**
     * 执行中 - Agent 正在处理任务
     */
    RUNNING,

    /**
     * 已完成 - 任务成功完成
     */
    COMPLETED,

    /**
     * 失败 - 任务执行失败
     */
    FAILED,

    /**
     * 已取消 - 任务被用户取消
     */
    CANCELLED
}

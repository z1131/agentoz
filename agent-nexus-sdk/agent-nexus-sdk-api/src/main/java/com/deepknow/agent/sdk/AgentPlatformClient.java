package com.deepknow.agent.sdk;

import com.deepknow.agent.sdk.agents.SessionHandle;

/**
 * 智能体平台 SDK 顶级客户端
 */
public interface AgentPlatformClient {

    /**
     * 开启一个新的智能体协作会话
     *
     * @param userId 用户 ID
     * @param title 会话标题
     * @return 会话句柄
     */
    SessionHandle openSession(String userId, String title);

    /**
     * 接入一个已存在的会话
     *
     * @param sessionId 会话 ID
     * @return 会话句柄
     */
    SessionHandle attachSession(String sessionId);
}

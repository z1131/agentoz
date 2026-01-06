package com.deepknow.agent.api;

import com.deepknow.agent.api.dto.*;

/**
 * 会话管理服务 - Dubbo 接口定义
 *
 * @author Agent Platform
 * @version 1.0.0
 */
public interface SessionService {

    /**
     * 创建会话
     *
     * @param request 创建请求
     * @return 会话信息
     */
    SessionDTO createSession(CreateSessionRequest request);

    /**
     * 获取会话信息
     *
     * @param sessionId 会话ID
     * @return 会话信息
     */
    SessionDTO getSession(String sessionId);

    /**
     * 更新会话状态
     *
     * @param sessionId 会话ID
     * @param status 状态
     * @return 更新后的会话信息
     */
    SessionDTO updateStatus(String sessionId, String status);

    /**
     * 添加消息到会话
     *
     * @param sessionId 会话ID
     * @param request 消息请求
     * @return 消息信息
     */
    MessageDTO addMessage(String sessionId, AddMessageRequest request);

    /**
     * 获取会话的所有消息
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    MessageListResponse getMessages(String sessionId);

    /**
     * 删除会话
     *
     * @param sessionId 会话ID
     */
    void deleteSession(String sessionId);
}

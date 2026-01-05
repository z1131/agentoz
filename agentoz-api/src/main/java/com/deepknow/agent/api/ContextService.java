package com.deepknow.agent.api;

import com.deepknow.agent.api.dto.*;

/**
 * 上下文管理服务 - Dubbo 接口定义
 *
 * @author Agent Platform
 * @version 1.0.0
 */
public interface ContextService {

    /**
     * 创建上下文
     *
     * @param request 创建请求
     * @return 上下文信息
     */
    ContextDTO createContext(CreateContextRequest request);

    /**
     * 获取上下文
     *
     * @param contextId 上下文ID
     * @return 上下文信息
     */
    ContextDTO getContext(String contextId);

    /**
     * 更新上下文
     *
     * @param contextId 上下文ID
     * @param data 上下文数据
     * @return 更新后的上下文信息
     */
    ContextDTO updateContext(String contextId, ContextData data);

    /**
     * 删除上下文
     *
     * @param contextId 上下文ID
     */
    void deleteContext(String contextId);
}

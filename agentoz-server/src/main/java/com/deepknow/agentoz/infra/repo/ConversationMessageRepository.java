package com.deepknow.agentoz.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agentoz.model.ConversationMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话消息仓储接口
 */
@Mapper
public interface ConversationMessageRepository extends BaseMapper<ConversationMessageEntity> {
}

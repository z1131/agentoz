package com.deepknow.agentoz.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agentoz.model.ConversationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话Repository
 *
 * <p>负责ConversationEntity的数据访问操作。</p>
 *
 * @see ConversationEntity
 */
@Mapper
public interface ConversationRepository extends BaseMapper<ConversationEntity> {
}

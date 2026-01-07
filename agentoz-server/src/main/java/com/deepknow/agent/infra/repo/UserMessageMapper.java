package com.deepknow.agent.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agent.model.UserMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserMessage Mapper
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessageEntity> {
}

package com.deepknow.nexus.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.nexus.model.UserMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserMessage Mapper
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessageEntity> {
}

package com.deepknow.platform.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.platform.model.UserMessageEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * UserMessage Mapper
 */
@Mapper
public interface UserMessageMapper extends BaseMapper<UserMessageEntity> {
}

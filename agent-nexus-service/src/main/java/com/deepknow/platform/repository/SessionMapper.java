package com.deepknow.platform.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.platform.model.SessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Session Mapper
 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}

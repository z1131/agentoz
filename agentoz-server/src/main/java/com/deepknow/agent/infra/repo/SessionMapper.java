package com.deepknow.nexus.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.nexus.model.SessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Session Mapper
 */
@Mapper
public interface SessionMapper extends BaseMapper<SessionEntity> {
}

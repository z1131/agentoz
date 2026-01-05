package com.deepknow.platform.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.platform.model.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}

package com.deepknow.nexus.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.nexus.model.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}

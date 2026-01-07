package com.deepknow.agent.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agent.model.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent Mapper
 */
@Mapper
public interface AgentMapper extends BaseMapper<AgentEntity> {
}

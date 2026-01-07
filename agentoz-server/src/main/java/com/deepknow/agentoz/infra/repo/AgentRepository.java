package com.deepknow.agentoz.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agentoz.model.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentRepository extends BaseMapper<AgentEntity> {
}

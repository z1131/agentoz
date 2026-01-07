package com.deepknow.agentozoz.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agentozoz.model.AgentEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentRepository extends BaseMapper<AgentEntity> {
}

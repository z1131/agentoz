package com.deepknow.agent.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agent.model.AgentCallLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AgentCallLog Mapper
 */
@Mapper
public interface AgentCallLogMapper extends BaseMapper<AgentCallLogEntity> {
}

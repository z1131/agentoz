package com.deepknow.nexus.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.nexus.model.AgentCallLogEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * AgentCallLog Mapper
 */
@Mapper
public interface AgentCallLogMapper extends BaseMapper<AgentCallLogEntity> {
}

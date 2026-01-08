package com.deepknow.agentoz.infra.repo;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.deepknow.agentoz.model.AgentConfigEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent配置Repository
 *
 * <p>负责AgentConfigEntity的数据访问操作。</p>
 *
 * <h3>🔍 常用查询场景</h3>
 * <ul>
 *   <li>根据configId查询配置</li>
 *   <li>根据模板标识查询预设配置</li>
 *   <li>根据标签查询相关配置</li>
 * </ul>
 *
 * @see AgentConfigEntity
 */
@Mapper
public interface AgentConfigRepository extends BaseMapper<AgentConfigEntity> {
}

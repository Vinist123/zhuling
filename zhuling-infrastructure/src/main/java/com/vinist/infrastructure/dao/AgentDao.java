package com.vinist.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vinist.infrastructure.dao.po.AgentPO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Agent DAO
 */
@Mapper
public interface AgentDao extends BaseMapper<AgentPO> {

}

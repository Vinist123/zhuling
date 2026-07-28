package com.vinist.domain.agent.adapter.repository;

import com.vinist.domain.agent.model.entity.AgentEntity;
import java.util.List;

/**
 * Agent 仓储接口
 */
public interface IAgentRepository {

    /**
     * 保存 Agent
     */
    String save(AgentEntity agent);

    /**
     * 根据 ID 查询
     */
    AgentEntity getById(String id);

    /**
     * 更新 Agent
     */
    void update(AgentEntity agent);

    /**
     * 删除 Agent
     */
    void deleteById(String id);

    /**
     * 获取所有 Agent
     */
    List<AgentEntity> listAll();

}

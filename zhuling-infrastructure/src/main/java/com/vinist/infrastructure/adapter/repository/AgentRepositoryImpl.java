package com.vinist.infrastructure.adapter.repository;

import com.vinist.domain.agent.adapter.repository.IAgentRepository;
import com.vinist.domain.agent.model.entity.AgentEntity;
import com.vinist.infrastructure.dao.AgentDao;
import com.vinist.infrastructure.dao.po.AgentPO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Agent 仓储实现
 */
@Slf4j
@Repository
public class AgentRepositoryImpl implements IAgentRepository {

    private final AgentDao agentDao;

    public AgentRepositoryImpl(AgentDao agentDao) {
        this.agentDao = agentDao;
    }

    @Override
    public String save(AgentEntity agent) {
        AgentPO po = convertToPO(agent);
        agentDao.insert(po);
        log.info("保存 Agent: id={}", po.getId());
        return po.getId();
    }

    @Override
    public AgentEntity getById(String id) {
        AgentPO po = agentDao.selectById(id);
        return convertToEntity(po);
    }

    @Override
    public void update(AgentEntity agent) {
        AgentPO po = convertToPO(agent);
        agentDao.updateById(po);
        log.info("更新 Agent: id={}", po.getId());
    }

    @Override
    public void deleteById(String id) {
        agentDao.deleteById(id);
        log.info("删除 Agent: id={}", id);
    }

    @Override
    public List<AgentEntity> listAll() {
        List<AgentPO> pos = agentDao.selectList(null);
        return pos.stream().map(this::convertToEntity).collect(Collectors.toList());
    }

    private AgentPO convertToPO(AgentEntity entity) {
        if (entity == null) return null;
        return AgentPO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .config(entity.getConfig())
                .status(entity.getStatus())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private AgentEntity convertToEntity(AgentPO po) {
        if (po == null) return null;
        return AgentEntity.builder()
                .id(po.getId())
                .name(po.getName())
                .description(po.getDescription())
                .config(po.getConfig())
                .status(po.getStatus())
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .build();
    }

}

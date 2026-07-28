package com.vinist.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vinist.infrastructure.dao.po.MessagePO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 消息 DAO
 */
@Mapper
public interface MessageDao extends BaseMapper<MessagePO> {


    /**
     * 删除会话的所有消息
     */
    @Select("DELETE FROM agent_message WHERE session_id = #{sessionId}")
    int deleteBySessionId(@Param("sessionId") String sessionId);

}

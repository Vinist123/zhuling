package com.vinist.infrastructure.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.vinist.infrastructure.dao.po.SessionPO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 会话 DAO
 */
@Mapper
public interface SessionDao extends BaseMapper<SessionPO> {

//    /**
//     * 根据对话目标和 User ID 查询
//     */
//    @Select("SELECT * FROM agent_session WHERE target_id = #{targetId} AND target_type = #{targetType} AND user_id = #{userId} ORDER BY created_at DESC LIMIT 1")
//    SessionPO selectByTargetIdAndTypeAndUserId(@Param("targetId") String targetId,
//                                               @Param("targetType") String targetType,
//                                               @Param("userId") String userId);

//    /**
//     * 根据 User ID 查询所有会话
//     */
//    @Select("SELECT * FROM agent_session WHERE user_id = #{userId} ORDER BY updated_at DESC")
//    List<SessionPO> selectByUserId(@Param("userId") String userId);

//    /**
//     * 按用户、目标和状态分页查询会话。
//     */
//    @Select("""
//            <script>
//            SELECT * FROM agent_session
//            WHERE user_id = #{userId}
//            <if test="targetId != null and targetId != ''">
//              AND target_id = #{targetId}
//            </if>
//            <if test="status != null and status != ''">
//              AND status = #{status}
//            </if>
//            ORDER BY updated_at DESC
//            LIMIT #{pageSize} OFFSET #{offset}
//            </script>
//            """)
//    List<SessionPO> selectPageByUserId(@Param("userId") String userId,
//                                       @Param("targetId") String targetId,
//                                       @Param("status") String status,
//                                       @Param("offset") int offset,
//                                       @Param("pageSize") int pageSize);

//    /**
//     * 统计按相同条件筛选后的会话数量。
//     */
//    @Select("""
//            <script>
//            SELECT COUNT(1) FROM agent_session
//            WHERE user_id = #{userId}
//            <if test="targetId != null and targetId != ''">
//              AND target_id = #{targetId}
//            </if>
//            <if test="status != null and status != ''">
//              AND status = #{status}
//            </if>
//            </script>
//            """)
//    long countByUserId(@Param("userId") String userId,
//                       @Param("targetId") String targetId,
//                       @Param("status") String status);

    /**
     * 增加消息计数
     */
    @Update("UPDATE agent_session SET message_count = message_count + 1, updated_at = NOW() WHERE id = #{sessionId}")
    int incrementMessageCount(@Param("sessionId") String sessionId);

}

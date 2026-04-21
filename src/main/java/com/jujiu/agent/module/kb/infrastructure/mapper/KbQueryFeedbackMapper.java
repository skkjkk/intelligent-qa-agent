package com.jujiu.agent.module.kb.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.kb.domain.entity.KbQueryFeedback;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * @author 17644
 */
@Mapper
public interface KbQueryFeedbackMapper extends BaseMapper<KbQueryFeedback> {
    @Select("""
            <script>
            SELECT SUM(CASE WHEN f.helpful = 1 THEN 1 ELSE 0 END) AS helpfulCount,
                   SUM(CASE WHEN f.helpful = 0 THEN 1 ELSE 0 END) AS unhelpfulCount,
                   AVG(f.rating) AS avgRating,
                   COUNT(1) AS totalFeedbacks
            FROM kb_query_feedback f
            INNER JOIN kb_query_log q ON q.id = f.query_log_id
            WHERE q.user_id = #{userId}
              <if test="kbId != null">AND q.kb_id = #{kbId}</if>
            </script>
            """)
    Map<String, Object> aggregateQuality(@Param("userId") Long userId, @Param("kbId") Long kbId);

    @Select("""
            <script>
            SELECT CAST(f.rating AS CHAR) AS dimName, COUNT(1) AS dimCount
            FROM kb_query_feedback f
            INNER JOIN kb_query_log q ON q.id = f.query_log_id
            WHERE q.user_id = #{userId}
              <if test="kbId != null">AND q.kb_id = #{kbId}</if>
              AND f.rating IS NOT NULL
            GROUP BY f.rating
            ORDER BY f.rating ASC
            </script>
            """)
    List<Map<String, Object>> aggregateRatingDistribution(@Param("userId") Long userId, @Param("kbId") Long kbId);
}

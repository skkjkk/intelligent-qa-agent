package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.KbDocument;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知识库文档仓库接口
 * 该接口继承自 BaseMapper 接口，用于对知识库文档进行数据库操作。
 * @author 17644
 * @date 2026/4/20
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper
public interface KbDocumentRepository extends BaseMapper<KbDocument> {
    @Select("""
            <script>
            SELECT file_type AS dimName, COUNT(1) AS dimCount
            FROM kb_document
            WHERE owner_user_id = #{userId}
              AND deleted = 0
              <if test="kbId != null">AND kb_id = #{kbId}</if>
            GROUP BY file_type
            ORDER BY dimCount DESC
            </script>
            """)
    List<Map<String, Object>> aggregateByFileType(@Param("userId") Long userId, @Param("kbId") Long kbId);

    @Select("""
            <script>
            SELECT status AS dimName, COUNT(1) AS dimCount
            FROM kb_document
            WHERE owner_user_id = #{userId}
              AND deleted = 0
              <if test="kbId != null">AND kb_id = #{kbId}</if>
            GROUP BY status
            ORDER BY dimCount DESC
            </script>
            """)
    List<Map<String, Object>> aggregateByStatus(@Param("userId") Long userId, @Param("kbId") Long kbId);

    @Select("""
            <script>
            SELECT DATE(created_at) AS dayVal, COUNT(1) AS dayCount
            FROM kb_document
            WHERE owner_user_id = #{userId}
              AND deleted = 0
              <if test="kbId != null">AND kb_id = #{kbId}</if>
              AND created_at &gt;= #{startAt}
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at) ASC
            </script>
            """)
    List<Map<String, Object>> aggregateCreatedTrend(@Param("userId") Long userId,
                                                    @Param("kbId") Long kbId,
                                                    @Param("startAt") LocalDateTime startAt);

    @Select("""
            <script>
            SELECT COUNT(1)
            FROM kb_document
            WHERE owner_user_id = #{userId}
              AND deleted = 0
              <if test="kbId != null">AND kb_id = #{kbId}</if>
              AND created_at &gt;= #{startAt}
            </script>
            """)
    Long countCreatedSince(@Param("userId") Long userId,
                           @Param("kbId") Long kbId,
                           @Param("startAt") LocalDateTime startAt);
}

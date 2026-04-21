package com.jujiu.agent.module.kb.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.kb.domain.entity.KbQueryLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 知识库查询日志仓库接口
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 9:40
 */
@Mapper
public interface KbQueryLogMapper extends BaseMapper<KbQueryLog> {
    /**
     * 聚合查询统计摘要
     * 
     * <p>根据用户ID和（可选的）知识库ID，聚合统计查询日志的各项指标。
     * 这是一个聚合查询，在一个SQL中完成多维度统计，避免多次查询。
     * 
     * <p>统计指标：
     * <ul>
     *     <li>totalQueries - 总查询次数</li>
     *     <li>successQueries - 成功查询次数（status = 'SUCCESS'）</li>
     *     <li>emptyQueries - 空结果查询次数（status = 'EMPTY'）</li>
     *     <li>failedQueries - 失败查询次数（status = 'FAILED'）</li>
     *     <li>avgLatencyMs - 平均响应延迟（毫秒）</li>
     *     <li>avgTotalTokens - 平均消耗Token数</li>
     * </ul>
     * 
     * <p>SQL说明：
     * <ul>
     *     <li>使用CASE WHEN实现条件计数，避免多次查询</li>
     *     <li>使用AVG()函数计算平均值，自动忽略NULL值</li>
     *     <li>使用MyBatis的&lt;script&gt;标签支持动态SQL</li>
     *     <li>kbId参数使用&lt;if test="kbId != null"&gt;实现可选条件</li>
     * </ul>
     * 
     * @param userId 用户ID，必填，用于限定查询范围
     * @param kbId 知识库ID，可选，用于限定到特定知识库；为null时查询用户所有知识库
     * @return 统计结果Map，键为SQL中定义的别名，值为对应的统计值
     *         可能返回的键: totalQueries, successQueries, emptyQueries, 
     *         failedQueries, avgLatencyMs, avgTotalTokens
     */
    @Select("""
            <script>
            SELECT COUNT(1) AS totalQueries,
                   SUM(CASE WHEN status = 'SUCCESS' THEN 1 ELSE 0 END) AS successQueries,
                   SUM(CASE WHEN status = 'EMPTY' THEN 1 ELSE 0 END) AS emptyQueries,
                   SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failedQueries,
                   AVG(latency_ms) AS avgLatencyMs,
                   AVG(total_tokens) AS avgTotalTokens
            FROM kb_query_log
            WHERE user_id = #{userId}
              <if test="kbId != null">AND kb_id = #{kbId}</if>
            </script>
            """)
    Map<String, Object> aggregateSummary(@Param("userId") Long userId, @Param("kbId") Long kbId);

    /**
     * 按日期聚合查询趋势
     * 
     * <p>查询指定时间范围内每天的查询次数，用于生成趋势图表数据。
     * 返回结果按日期升序排列，便于前端绑定折线图等可视化组件。
     * 
     * <p>使用场景：
     * <ul>
     *     <li>知识库使用趋势分析</li>
     *     <li>日活跃用户统计</li>
     *     <li>查询量变化趋势监控</li>
     * </ul>
     * 
     * <p>SQL说明：
     * <ul>
     *     <li>DATE(created_at) - 将时间戳转换为日期，去除时间部分</li>
     *     <li>GROUP BY DATE(created_at) - 按日期分组统计</li>
     *     <li>ORDER BY DATE(created_at) ASC - 按日期升序排列</li>
     *     <li>&gt;= 表示包含起始日期</li>
     * </ul>
     * 
     * <p>返回数据示例：
     * <pre>
     * [
     *     {"dayVal": "2026-04-15", "dayCount": 120},
     *     {"dayVal": "2026-04-16", "dayCount": 150},
     *     {"dayVal": "2026-04-17", "dayCount": 180},
     *     ...
     * ]
     * </pre>
     * 
     * @param userId 用户ID，必填
     * @param kbId 知识库ID，可选，为null时查询用户所有知识库
     * @param startAt 起始时间，必填，用于限定查询范围
     * @return 每天的查询统计列表，按日期升序排列
     */
    @Select("""
            <script>
            SELECT DATE(created_at) AS dayVal, COUNT(1) AS dayCount
            FROM kb_query_log
            WHERE user_id = #{userId}
              <if test="kbId != null">AND kb_id = #{kbId}</if>
              AND created_at &gt;= #{startAt}
            GROUP BY DATE(created_at)
            ORDER BY DATE(created_at) ASC
            </script>
            """)
    List<Map<String, Object>> aggregateTrend(@Param("userId") Long userId,
                                             @Param("kbId") Long kbId,
                                             @Param("startAt") LocalDateTime startAt);

    /**
     * 统计指定时间范围内的查询次数
     * 
     * <p>这是一个简单的计数查询，用于统计满足条件的查询日志总条数。
     * 比aggregateSummary更轻量，仅返回计数结果，不做复杂聚合。
     * 
     * <p>使用场景：
     * <ul>
     *     <li>统计今日/本周/本月查询量</li>
     *     <li>判断知识库是否有查询记录</li>
     *     <li>分页查询总数（配合LIMIT使用）</li>
     * </ul>
     * 
     * <p>SQL说明：
     * <ul>
     *     <li>COUNT(1) - 统计满足条件的行数，等价于COUNT(*)</li>
     *     <li>&gt;= 表示包含起始时间点</li>
     *     <li>&lt;if test="kbId != null"&gt; 实现可选的知识库过滤</li>
     * </ul>
     * 
     * <p>与aggregateSummary的区别：
     * <ul>
     *     <li>countSince: 仅返回计数，性能更好</li>
     *     <li>aggregateSummary: 返回多维度统计，SQL更复杂</li>
     * </ul>
     * 
     * @param userId 用户ID，必填
     * @param kbId 知识库ID，可选，为null时查询用户所有知识库
     * @param startAt 起始时间，必填
     * @return 满足条件的查询日志总条数
     */
    @Select("""
            <script>
            SELECT COUNT(1)
            FROM kb_query_log
            WHERE user_id = #{userId}
              <if test="kbId != null">AND kb_id = #{kbId}</if>
              AND created_at &gt;= #{startAt}
            </script>
            """)
    Long countSince(@Param("userId") Long userId,
                    @Param("kbId") Long kbId,
                    @Param("startAt") LocalDateTime startAt);
}
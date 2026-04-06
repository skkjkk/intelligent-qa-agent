package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.KbQueryLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/1 9:40
 */
@Mapper
public interface KbQueryLogRepository extends BaseMapper<KbQueryLog> {
}

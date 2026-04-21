package com.jujiu.agent.module.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.chat.domain.entity.Session;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 */
@Mapper
public interface SessionMapper extends BaseMapper<Session> {
}

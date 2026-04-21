package com.jujiu.agent.module.auth.infrastructure.mapper;

import com.jujiu.agent.module.auth.domain.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 15:00
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}


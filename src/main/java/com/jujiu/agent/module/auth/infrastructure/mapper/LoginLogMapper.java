package com.jujiu.agent.module.auth.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.auth.domain.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 */
@Mapper
public interface LoginLogMapper extends BaseMapper<LoginLog> {
    
}

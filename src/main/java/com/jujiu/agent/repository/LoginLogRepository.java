package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.LoginLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 */
@Mapper
public interface LoginLogRepository extends BaseMapper<LoginLog> {
    
}

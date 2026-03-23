package com.jujiu.agent.repository;

import com.jujiu.agent.model.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/3/20 15:00
 */
@Mapper
public interface UserRepository extends BaseMapper<User> {
    // MyBatis-Plus 已提供基本的 CRUD 方法
    // 不需要额外定义，UserMapper.xml 可以按需添加自定义SQL
}


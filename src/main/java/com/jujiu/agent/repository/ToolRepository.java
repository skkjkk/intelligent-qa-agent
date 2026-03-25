package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.Tool;
import org.apache.ibatis.annotations.Mapper;

/**
 * * 工具数据访问层
 *  * 继承 MyBatis-Plus 的 BaseMapper，自动获得 CRUD 方法
 * @author 17644
 */
@Mapper
public interface ToolRepository extends BaseMapper<Tool> {
}

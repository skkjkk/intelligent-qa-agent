package com.jujiu.agent.module.chat.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.chat.domain.entity.Message;
import org.apache.ibatis.annotations.Mapper;

/**
 * @author 17644
 */
@Mapper
public interface MessageMapper extends BaseMapper<Message> {
}

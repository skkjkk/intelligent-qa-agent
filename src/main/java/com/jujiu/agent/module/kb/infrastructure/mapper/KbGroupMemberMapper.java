package com.jujiu.agent.module.kb.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.kb.domain.entity.KbGroupMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGroupMemberMapper extends BaseMapper<KbGroupMember> {
}

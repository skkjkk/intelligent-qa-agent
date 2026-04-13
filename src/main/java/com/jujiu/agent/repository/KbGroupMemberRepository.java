package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.KbGroupMember;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbGroupMemberRepository extends BaseMapper<KbGroupMember> {
}

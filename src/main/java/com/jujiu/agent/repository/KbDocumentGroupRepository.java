package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.KbDocumentGroup;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbDocumentGroupRepository extends BaseMapper<KbDocumentGroup> {
}

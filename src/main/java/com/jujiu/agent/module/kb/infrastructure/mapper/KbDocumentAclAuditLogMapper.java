package com.jujiu.agent.module.kb.infrastructure.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.module.kb.domain.entity.KbDocumentAclAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbDocumentAclAuditLogMapper extends BaseMapper<KbDocumentAclAuditLog> {
}

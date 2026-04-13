package com.jujiu.agent.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jujiu.agent.model.entity.KbDocumentAclAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface KbDocumentAclAuditLogRepository extends BaseMapper<KbDocumentAclAuditLog> {
}

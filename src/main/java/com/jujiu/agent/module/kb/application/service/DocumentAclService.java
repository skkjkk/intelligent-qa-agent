package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.domain.entity.KbDocument;

import java.util.Collection;
import java.util.Set;

/**
 * 文档 ACL 服务接口。定义文档访问控制列表相关的权限校验能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 13:38
 */
public interface DocumentAclService {
    
    /**
     * 判断是否启用了 ACL。
     *
     * @return 如果启用了 ACL 则返回 {@code true}，否则返回 {@code false}
     */
    boolean isAclEnabled();
    
    /**
     * 判断指定用户是否拥有对文档的读取权限。
     *
     * @param userId   用户 ID
     * @param document 知识库文档
     * @return 如果用户可读取该文档则返回 {@code true}，否则返回 {@code false}
     */
    boolean canRead(Long userId, KbDocument document);

    /**
     * 判断指定用户是否拥有对文档的管理权限。
     *
     * @param userId   用户 ID
     * @param document 知识库文档
     * @return 如果用户可管理该文档则返回 {@code true}，否则返回 {@code false}
     */
    boolean canManage(Long userId, KbDocument document);
    
    /**
     * 判断指定用户是否拥有对文档的分享权限。
     *
     * @param userId   用户 ID
     * @param document 知识库文档
     * @return 如果用户可分享该文档则返回 {@code true}，否则返回 {@code false}
     */
    boolean canShare(Long userId, KbDocument document);
    /**
     * 从给定的文档 ID 集合中筛选出当前用户有读取权限的文档 ID。
     *
     * @param userId      用户 ID
     * @param documentIds 文档 ID 集合
     * @return 用户有读取权限的文档 ID 集合，保持原始顺序
     */
    Set<Long> filterReadableDocumentIds(Long userId, Collection<Long> documentIds);

    /**
     * 列出指定用户在指定知识库下有读取权限的所有文档 ID。
     *
     * @param userId 用户 ID
     * @param kbId   知识库 ID，若为 {@code null} 则查询所有知识库
     * @return 用户有读取权限的文档 ID 集合
     */
    Set<Long> listReadableDocumentIds(Long userId, Long kbId);
}

package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.request.BindDocumentGroupRequest;
import com.jujiu.agent.module.kb.api.response.KbDocumentGroupResponse;

import java.util.List;

public interface DocumentGroupService {

    /**
     * 列出所有文档组
     *
     * @param userId 用户id
     * @param documentId 文档id
     * @return 文档组列表
     */
    List<KbDocumentGroupResponse> listDocumentGroups(Long userId, Long documentId);

    /**
     * 绑定文档组
     *
     * @param userId 用户id
     * @param documentId 文档id
     * @param request 绑定请求
     */
    void bindDocumentGroup(Long userId, Long documentId, BindDocumentGroupRequest request);

    /**
     * 解绑文档组
     *
     * @param userId 用户id
     * @param documentId 文档id
     * @param groupId 组id
     */
    void unbindDocumentGroup(Long userId, Long documentId, Long groupId);
}

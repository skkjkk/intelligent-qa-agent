package com.jujiu.agent.service.kb;

import com.jujiu.agent.model.dto.request.BindDocumentGroupRequest;
import com.jujiu.agent.model.dto.response.KbDocumentGroupResponse;

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

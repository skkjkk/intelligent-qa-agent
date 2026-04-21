package com.jujiu.agent.module.kb.application.service;

import com.jujiu.agent.module.kb.api.request.AddKbGroupMemberRequest;
import com.jujiu.agent.module.kb.api.request.CreateKbGroupRequest;
import com.jujiu.agent.module.kb.api.response.KbGroupMemberResponse;
import com.jujiu.agent.module.kb.api.response.KbGroupResponse;

import java.util.List;

public interface KbGroupService {
    /**
     * 创建群组
     *
     * @param userId 用户id
     * @param request 请求
     * @return 群组
     */
    KbGroupResponse createGroup(Long userId, CreateKbGroupRequest request);

    /**
     * 添加群组成员
     *
     * @param userId 用户id
     * @param groupId 群组id
     * @param request 请求
     */
    void addMember(Long userId, Long groupId, AddKbGroupMemberRequest request);

    /**
     * 移除群组成员
     *
     * @param userId 用户id
     * @param groupId 群组id
     * @param memberUserId 成员用户id
     */
    void removeMember(Long userId, Long groupId, Long memberUserId);

    /**
     * 列出群组成员
     *
     * @param userId 用户id
     * @param groupId 群组id
     * @return 群组成员
     */
    List<KbGroupMemberResponse> listMembers(Long userId, Long groupId);

    /**
     * 列出用户群组
     *
     * @param userId 用户id
     * @return 群组
     */
    List<KbGroupResponse> listMyGroups(Long userId);

}

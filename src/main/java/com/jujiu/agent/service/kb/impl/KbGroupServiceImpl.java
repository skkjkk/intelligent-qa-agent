package com.jujiu.agent.service.kb.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.common.exception.BusinessException;
import com.jujiu.agent.common.result.ResultCode;
import com.jujiu.agent.model.dto.request.AddKbGroupMemberRequest;
import com.jujiu.agent.model.dto.request.CreateKbGroupRequest;
import com.jujiu.agent.model.dto.response.KbGroupMemberResponse;
import com.jujiu.agent.model.dto.response.KbGroupResponse;
import com.jujiu.agent.model.entity.KbGroup;
import com.jujiu.agent.model.entity.KbGroupMember;
import com.jujiu.agent.repository.KbGroupMemberRepository;
import com.jujiu.agent.repository.KbGroupRepository;
import com.jujiu.agent.service.kb.KbGroupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:47
 */
@Service
public class KbGroupServiceImpl implements KbGroupService {
    private static final String ROLE_OWNER = "OWNER";
    private static final String ROLE_MEMBER = "MEMBER";

    private final KbGroupRepository kbGroupRepository;
    private final KbGroupMemberRepository kbGroupMemberRepository;

    public KbGroupServiceImpl(KbGroupRepository kbGroupRepository,
                              KbGroupMemberRepository kbGroupMemberRepository) {
        this.kbGroupRepository = kbGroupRepository;
        this.kbGroupMemberRepository = kbGroupMemberRepository;
    }

    @Override
    @Transactional
    public KbGroupResponse createGroup(Long userId, CreateKbGroupRequest request) {
        validateCreateRequest(request);

        KbGroup existing = kbGroupRepository.selectOne(
                new LambdaQueryWrapper<KbGroup>()
                        .eq(KbGroup::getCode, request.getCode())
                        .last("LIMIT 1")
        );

        if (existing != null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "group code 已存在");
        }

        KbGroup group = KbGroup.builder()
                .name(request.getName())
                .code(request.getCode())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        kbGroupRepository.insert(group);

        KbGroupMember member = KbGroupMember.builder()
                .userId(userId)
                .groupId(group.getId())
                .role(ROLE_OWNER)
                .createdAt(LocalDateTime.now())
                .build();
        kbGroupMemberRepository.insert(member);
        
        return KbGroupResponse.builder()
                .code(group.getCode())
                .name(group.getName())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public void addMember(Long userId, Long groupId, AddKbGroupMemberRequest request) {
        requireGroupOwner(userId, groupId);

        String role = normalizeRole(request.getRole());

        KbGroupMember existing = kbGroupMemberRepository.selectOne(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getUserId, request.getUserId())
                        .eq(KbGroupMember::getGroupId, groupId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }
        KbGroupMember member = KbGroupMember.builder()
                .userId(request.getUserId())
                .groupId(groupId)
                .role(role)
                .createdAt(LocalDateTime.now())
                .build();
        kbGroupMemberRepository.insert(member);
    }

    @Override
    @Transactional
    public void removeMember(Long userId, Long groupId, Long memberUserId) {
        requireGroupOwner(userId, groupId);
        KbGroup group = requireGroup(groupId);
        if (group.getCreatedBy().equals(memberUserId)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "不能移除组 OWNER");
        }
        kbGroupMemberRepository.delete(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getUserId, memberUserId)
                        .eq(KbGroupMember::getGroupId, groupId)
        );
    }

    @Override
    public List<KbGroupMemberResponse> listMembers(Long userId, Long groupId) {
        requireGroupOwner(userId, groupId);
        return kbGroupMemberRepository.selectList(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getGroupId, groupId)
                        .orderByAsc(KbGroupMember::getCreatedAt)
        ).stream().map(member -> KbGroupMemberResponse.builder()
                .id(member.getId())
                .groupId(member.getGroupId())
                .userId(member.getUserId())
                .role(member.getRole())
                .createdAt(member.getCreatedAt())
                .build()
        ).toList();
    }
    
    private void requireGroupOwner(Long userId, Long groupId) {
        requireGroup(groupId);

        KbGroupMember member = kbGroupMemberRepository.selectOne(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getGroupId, groupId)
                        .eq(KbGroupMember::getUserId, userId)
                        .last("LIMIT 1")
        );
        if (member == null || !ROLE_OWNER.equalsIgnoreCase(member.getRole())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "无组管理权限");
        }
    }
    
    private void validateCreateRequest(CreateKbGroupRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "request 不能为空");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "name 不能为空");
        }
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "code 不能为空");
        }
    }

    private KbGroup requireGroup(Long groupId) {
        KbGroup group = kbGroupRepository.selectById(groupId);
        if (group == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "group 不存在");
        }
        return group;
    }
    
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return ROLE_MEMBER;
        }
        if (ROLE_OWNER.equalsIgnoreCase(role)) {
            return ROLE_OWNER;
        }
        return ROLE_MEMBER;
    }
}

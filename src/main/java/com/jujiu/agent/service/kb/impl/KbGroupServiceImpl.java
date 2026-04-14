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
 * 知识库用户组服务实现类。
 *
 * <p>提供用户组的创建、成员管理以及组成员列表查询等功能。
 * 组创建者自动成为 OWNER，只有 OWNER 可以管理组成员。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:47
 */
@Service
public class KbGroupServiceImpl implements KbGroupService {
    /** 组角色：所有者 */
    private static final String ROLE_OWNER = "OWNER";
    /** 组角色：成员 */
    private static final String ROLE_MEMBER = "MEMBER";

    /** 用户组数据仓库 */
    private final KbGroupRepository kbGroupRepository;
    /** 组成员数据仓库 */
    private final KbGroupMemberRepository kbGroupMemberRepository;

    /**
     * 构造方法。
     *
     * @param kbGroupRepository 用户组数据仓库
     * @param kbGroupMemberRepository 组成员数据仓库
     */
    public KbGroupServiceImpl(KbGroupRepository kbGroupRepository,
                              KbGroupMemberRepository kbGroupMemberRepository) {
        this.kbGroupRepository = kbGroupRepository;
        this.kbGroupMemberRepository = kbGroupMemberRepository;
    }

    /**
     * 创建用户组。
     *
     * @param userId 当前用户 ID
     * @param request 创建用户组请求
     * @return 创建后的用户组信息
     * @throws BusinessException 参数校验失败或组编码已存在时抛出
     */
    @Override
    @Transactional
    public KbGroupResponse createGroup(Long userId, CreateKbGroupRequest request) {
        // 1. 校验创建请求参数
        validateCreateRequest(request);

        // 2. 检查 group code 是否已存在
        KbGroup existing = kbGroupRepository.selectOne(
                new LambdaQueryWrapper<KbGroup>()
                        .eq(KbGroup::getCode, request.getCode())
                        .last("LIMIT 1")
        );

        if (existing != null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "group code 已存在");
        }

        // 3. 构建并插入用户组记录
        KbGroup group = KbGroup.builder()
                .name(request.getName())
                .code(request.getCode())
                .createdBy(userId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        kbGroupRepository.insert(group);

        // 4. 将当前用户插入为组 OWNER
        KbGroupMember member = KbGroupMember.builder()
                .userId(userId)
                .groupId(group.getId())
                .role(ROLE_OWNER)
                .createdAt(LocalDateTime.now())
                .build();
        kbGroupMemberRepository.insert(member);
        
        // 5. 组装并返回响应对象
        return KbGroupResponse.builder()
                .code(group.getCode())
                .name(group.getName())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .build();
    }

    /**
     * 添加组成员。
     *
     * @param userId 当前用户 ID
     * @param groupId 用户组 ID
     * @param request 添加成员请求
     * @throws BusinessException 无管理权限时抛出
     */
    @Override
    @Transactional
    public void addMember(Long userId, Long groupId, AddKbGroupMemberRequest request) {
        // 1. 校验当前用户是否为组 OWNER
        requireGroupOwner(userId, groupId);

        // 2. 规范化成员角色
        String role = normalizeRole(request.getRole());

        // 3. 检查该用户是否已在组中
        KbGroupMember existing = kbGroupMemberRepository.selectOne(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getUserId, request.getUserId())
                        .eq(KbGroupMember::getGroupId, groupId)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return;
        }

        // 4. 构建并插入组成员记录
        KbGroupMember member = KbGroupMember.builder()
                .userId(request.getUserId())
                .groupId(groupId)
                .role(role)
                .createdAt(LocalDateTime.now())
                .build();
        kbGroupMemberRepository.insert(member);
    }

    /**
     * 移除组成员。
     *
     * @param userId 当前用户 ID
     * @param groupId 用户组 ID
     * @param memberUserId 待移除成员的用户 ID
     * @throws BusinessException 无管理权限或试图移除 OWNER 时抛出
     */
    @Override
    @Transactional
    public void removeMember(Long userId, Long groupId, Long memberUserId) {
        // 1. 校验当前用户是否为组 OWNER
        requireGroupOwner(userId, groupId);

        // 2. 确认用户组存在
        KbGroup group = requireGroup(groupId);

        // 3. 禁止移除组 OWNER
        if (group.getCreatedBy().equals(memberUserId)) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "不能移除组 OWNER");
        }

        // 4. 删除组成员记录
        kbGroupMemberRepository.delete(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getUserId, memberUserId)
                        .eq(KbGroupMember::getGroupId, groupId)
        );
    }

    /**
     * 查询组成员列表。
     *
     * @param userId 当前用户 ID
     * @param groupId 用户组 ID
     * @return 组成员列表
     * @throws BusinessException 无管理权限时抛出
     */
    @Override
    public List<KbGroupMemberResponse> listMembers(Long userId, Long groupId) {
        // 1. 校验当前用户是否为组 OWNER
        requireGroupOwner(userId, groupId);

        // 2. 查询组成员并按创建时间升序排列
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
    
    /**
     * 查询当前用户所属的所有组。
     *
     * @param userId 当前用户 ID
     * @return 用户组列表
     * @throws BusinessException userId 不合法时抛出
     */
    @Override
    public List<KbGroupResponse> listMyGroups(Long userId) {
        // 1. 校验 userId 参数
        if (userId == null || userId <= 0) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "userId 不能为空");
        }

        // 2. 查询当前用户的所有组成员关系
        List<KbGroupMember> memberships = kbGroupMemberRepository.selectList(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getUserId, userId)
                        .orderByAsc(KbGroupMember::getCreatedAt)
        );

        // 3. 若无成员关系则返回空列表
        if (memberships.isEmpty()) {
            return List.of();
        }

        // 4. 提取组 ID 列表
        List<Long> groupIds = memberships.stream()
                .map(KbGroupMember::getGroupId)
                .distinct()
                .toList();

        // 5. 查询组信息并组装响应
        return kbGroupRepository.selectList(
                new LambdaQueryWrapper<KbGroup>()
                        .in(KbGroup::getId, groupIds)
                        .orderByAsc(KbGroup::getCreatedAt)
        ).stream().map(group -> KbGroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .code(group.getCode())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .build()
        ).toList();
    }


    /**
     * 要求当前用户为指定组的 OWNER。
     *
     * @param userId 当前用户 ID
     * @param groupId 用户组 ID
     * @throws BusinessException 组不存在或无管理权限时抛出
     */
    private void requireGroupOwner(Long userId, Long groupId) {
        // 1. 确认用户组存在
        requireGroup(groupId);

        // 2. 查询当前用户在该组中的成员记录
        KbGroupMember member = kbGroupMemberRepository.selectOne(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getGroupId, groupId)
                        .eq(KbGroupMember::getUserId, userId)
                        .last("LIMIT 1")
        );

        // 3. 校验角色是否为 OWNER
        if (member == null || !ROLE_OWNER.equalsIgnoreCase(member.getRole())) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "无组管理权限");
        }
    }
    
    /**
     * 校验创建用户组请求参数。
     *
     * @param request 创建用户组请求
     * @throws BusinessException 参数校验失败时抛出
     */
    private void validateCreateRequest(CreateKbGroupRequest request) {
        // 1. 校验请求对象不能为空
        if (request == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "request 不能为空");
        }

        // 2. 校验组名称不能为空
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "name 不能为空");
        }

        // 3. 校验组编码不能为空
        if (request.getCode() == null || request.getCode().isBlank()) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "code 不能为空");
        }
    }

    /**
     * 根据 ID 查询用户组，不存在则抛出异常。
     *
     * @param groupId 用户组 ID
     * @return 用户组实体
     * @throws BusinessException 组不存在时抛出
     */
    private KbGroup requireGroup(Long groupId) {
        // 1. 根据 ID 查询用户组
        KbGroup group = kbGroupRepository.selectById(groupId);

        // 2. 不存在则抛出业务异常
        if (group == null) {
            throw new BusinessException(ResultCode.INVALID_PARAMETER, "group 不存在");
        }

        return group;
    }
    
    /**
     * 规范化组角色字符串。
     *
     * @param role 原始角色字符串
     * @return 规范化后的角色，默认为 MEMBER
     */
    private String normalizeRole(String role) {
        // 1. 若为空值则返回默认 MEMBER 角色
        if (role == null || role.isBlank()) {
            return ROLE_MEMBER;
        }

        // 2. 若角色为 OWNER 则返回 OWNER
        if (ROLE_OWNER.equalsIgnoreCase(role)) {
            return ROLE_OWNER;
        }

        // 3. 其余情况返回 MEMBER
        return ROLE_MEMBER;
    }
}

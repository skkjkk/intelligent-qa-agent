package com.jujiu.agent.module.kb.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jujiu.agent.module.kb.domain.entity.KbGroupMember;
import com.jujiu.agent.module.kb.infrastructure.mapper.KbGroupMemberMapper;
import com.jujiu.agent.module.kb.application.service.GroupMembershipService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户组成员关系服务实现类。提供查询用户所属组及判断组成员关系的能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 16:30
 */
@Service
@Slf4j
public class GroupMembershipServiceImpl implements GroupMembershipService {
    /** 知识库组成员仓储。 */
    private final KbGroupMemberMapper kbGroupMemberMapper;

    /**
     * 构造方法。
     *
     * @param kbGroupMemberMapper 知识库组成员仓储
     */
    public GroupMembershipServiceImpl(KbGroupMemberMapper kbGroupMemberMapper) {
        this.kbGroupMemberMapper = kbGroupMemberMapper;
    }
    
    /**
     * 查询指定用户所属的所有组 ID 集合。
     *
     * @param userId 用户 ID
     * @return 该用户所属的组 ID 集合，若用户未加入任何组则返回空集合
     */
    @Override
    public Set<Long> listGroupIdsByUserId(Long userId) {
        // 1. 参数校验，若用户 ID 为空则返回空集合
        if (userId == null) {
            return Set.of();
        }
        // 2. 查询该用户的所有组成员记录，并提取组 ID 集合
        return kbGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<KbGroupMember>()
                                .eq(KbGroupMember::getUserId, userId)
                ).stream()
                .map(KbGroupMember::getGroupId)
                .collect(Collectors.toSet()
                );
    }

    /**
     * 判断指定用户是否属于指定组。
     *
     * @param userId  用户 ID
     * @param groupId 组 ID
     * @return 如果用户属于该组则返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean isMember(Long userId, Long groupId) {
        // 1. 参数校验，若任一参数为空则直接返回 false
        if (userId == null || groupId == null) {
            return false;
        }

        // 2. 查询匹配的用户-组关联记录数
        Long count = kbGroupMemberMapper.selectCount(
                new LambdaQueryWrapper<KbGroupMember>()
                        .eq(KbGroupMember::getUserId, userId)
                        .eq(KbGroupMember::getGroupId, groupId)
        );
        // 3. 若记录数大于 0，则表示用户属于该组
        return count != null && count > 0;
    }
}

package com.jujiu.agent.service.kb;

import java.util.Set;

/**
 * 用户组成员关系服务接口。提供查询用户所属组及判断组成员关系的能力。
 *
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 16:29
 */
public interface GroupMembershipService {
    /**
     * 查询指定用户所属的所有组 ID 集合。
     *
     * @param userId 用户 ID
     * @return 该用户所属的组 ID 集合，若用户未加入任何组则返回空集合
     */
    Set<Long> listGroupIdsByUserId(Long userId);

    /**
     * 判断指定用户是否属于指定组。
     *
     * @param userId  用户 ID
     * @param groupId 组 ID
     * @return 如果用户属于该组则返回 {@code true}，否则返回 {@code false}
     */
    boolean isMember(Long userId, Long groupId);
}

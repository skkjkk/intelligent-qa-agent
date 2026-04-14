package com.jujiu.agent.controller;

import com.jujiu.agent.common.result.Result;
import com.jujiu.agent.model.dto.request.AddKbGroupMemberRequest;
import com.jujiu.agent.model.dto.request.CreateKbGroupRequest;
import com.jujiu.agent.model.dto.response.KbGroupMemberResponse;
import com.jujiu.agent.model.dto.response.KbGroupResponse;
import com.jujiu.agent.service.kb.KbGroupService;
import com.jujiu.agent.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author 17644
 * @version 1.0.0
 * @since 2026/4/13 19:58
 */
@RestController
@RequestMapping("/api/v1/kb/groups")
@Tag(name = "知识库用户组管理", description = "用户组创建、成员管理")
public class KbGroupController {
    private final KbGroupService kbGroupService;

    public KbGroupController(KbGroupService kbGroupService) {
        this.kbGroupService = kbGroupService;
    }

    private Long getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }

    @PostMapping
    @Operation(summary = "创建用户组")
    public Result<KbGroupResponse> createGroup(@RequestBody @Valid CreateKbGroupRequest request) {
        Long userId = getCurrentUserId();
        return Result.success(kbGroupService.createGroup(userId, request));
    }

    @PostMapping("/{groupId}/members")
    @Operation(summary = "添加组成员")
    public Result<Void> addMember(@PathVariable Long groupId,
                                  @RequestBody @Valid AddKbGroupMemberRequest request) {
        Long userId = getCurrentUserId();
        kbGroupService.addMember(userId, groupId, request);
        return Result.success(null, "添加成员成功");
    }

    @DeleteMapping("/{groupId}/members/{memberUserId}")
    @Operation(summary = "移除组成员")
    public Result<Void> removeMember(@PathVariable Long groupId,
                                     @PathVariable Long memberUserId) {
        Long userId = getCurrentUserId();
        kbGroupService.removeMember(userId, groupId, memberUserId);
        return Result.success(null, "移除成员成功");
    }

    @GetMapping("/{groupId}/members")
    @Operation(summary = "查询组成员")
    public Result<List<KbGroupMemberResponse>> listMembers(@PathVariable Long groupId) {
        Long userId = getCurrentUserId();
        return Result.success(kbGroupService.listMembers(userId, groupId));
    }

    @GetMapping
    @Operation(summary = "查询当前用户所属用户组")
    public Result<List<KbGroupResponse>> listMyGroups() {
        Long userId = getCurrentUserId();
        return Result.success(kbGroupService.listMyGroups(userId));
    }

}

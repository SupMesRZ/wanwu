package com.huohuo.scenicaiguide.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.system.dto.request.RoleSaveRequest;
import com.huohuo.scenicaiguide.system.dto.response.PermissionCatalogResponse;
import com.huohuo.scenicaiguide.system.dto.response.RoleResponse;
import com.huohuo.scenicaiguide.system.entity.SysRole;
import com.huohuo.scenicaiguide.system.entity.SysRolePermission;
import com.huohuo.scenicaiguide.system.entity.SysUserRole;
import com.huohuo.scenicaiguide.system.mapper.SysRoleMapper;
import com.huohuo.scenicaiguide.system.mapper.SysRolePermissionMapper;
import com.huohuo.scenicaiguide.system.mapper.SysUserRoleMapper;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 角色管理服务
 * <p>
 * 内置角色允许改名和调整权限，但不可删除，避免把系统调成"谁都管不了"的状态
 */
@Service
public class RoleService {

    private static final int MAX_PAGE_SIZE = 100;

    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final UserPermissionService userPermissionService;

    public RoleService(SysRoleMapper roleMapper,
                       SysRolePermissionMapper rolePermissionMapper,
                       SysUserRoleMapper userRoleMapper,
                       UserPermissionService userPermissionService) {
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.userRoleMapper = userRoleMapper;
        this.userPermissionService = userPermissionService;
    }

    // ========== 查询 ==========

    public PageResponse<RoleResponse> page(String keyword, long page, long pageSize) {
        long safePage = Math.max(page, 1);
        long safeSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);

        LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId);
        if (StringUtils.hasText(keyword)) {
            String trimmed = keyword.trim();
            query.and(wrapper -> wrapper
                .like(SysRole::getCode, trimmed)
                .or().like(SysRole::getName, trimmed));
        }

        Page<SysRole> result = roleMapper.selectPage(new Page<>(safePage, safeSize), query);
        List<RoleResponse> records = result.getRecords().stream()
            .map(role -> toResponse(role, permissionsOfRole(role.getId())))
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    /**
     * 全量角色列表，供用户编辑页的角色下拉使用
     */
    public List<RoleResponse> listEnabled() {
        List<SysRole> roles = roleMapper.selectList(
            new LambdaQueryWrapper<SysRole>().eq(SysRole::getStatus, 1).orderByAsc(SysRole::getId));
        return roles.stream().map(role -> toResponse(role, List.of())).toList();
    }

    public RoleResponse detail(Long id) {
        SysRole role = getExisting(id);
        return toResponse(role, permissionsOfRole(id));
    }

    /**
     * 权限清单，按模块分组
     */
    public List<PermissionCatalogResponse> permissionCatalog() {
        Map<String, List<PermissionCode>> grouped = PermissionCode.groupByModule();
        List<PermissionCatalogResponse> catalog = new ArrayList<>();
        grouped.forEach((module, items) -> {
            PermissionCatalogResponse group = new PermissionCatalogResponse();
            group.setModule(module);
            group.setPermissions(items.stream().map(item -> {
                PermissionCatalogResponse.PermissionItem entry = new PermissionCatalogResponse.PermissionItem();
                entry.setCode(item.getCode());
                entry.setDescription(item.getDescription());
                return entry;
            }).toList());
            catalog.add(group);
        });
        return catalog;
    }

    // ========== 写操作 ==========

    @Transactional
    public RoleResponse create(RoleSaveRequest request) {
        if (!StringUtils.hasText(request.getCode())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色标识不能为空");
        }
        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色名称不能为空");
        }
        requireCodeAvailable(request.getCode(), null);

        SysRole role = new SysRole();
        role.setCode(request.getCode().trim());
        role.setName(request.getName().trim());
        role.setDescription(request.getDescription());
        role.setBuiltin(0);
        role.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        role.setCreatedAt(LocalDateTime.now());
        role.setUpdatedAt(LocalDateTime.now());
        role.setDeleted(0);
        roleMapper.insert(role);

        List<String> permissions = replacePermissions(role.getId(), request.getPermissions());
        return toResponse(role, permissions);
    }

    @Transactional
    public RoleResponse update(Long id, RoleSaveRequest request) {
        SysRole role = getExisting(id);
        if (StringUtils.hasText(request.getName())) {
            role.setName(request.getName().trim());
        }
        role.setDescription(request.getDescription());
        if (request.getStatus() != null) {
            role.setStatus(request.getStatus());
        }
        role.setUpdatedAt(LocalDateTime.now());
        roleMapper.updateById(role);

        List<String> permissions = request.getPermissions() == null
            ? permissionsOfRole(id)
            : replacePermissions(id, request.getPermissions());

        // 改名不影响授权，但改状态或改勾选都会，统一失效更省心
        userPermissionService.evictByRole(id);
        return toResponse(role, permissions);
    }

    /**
     * 单独更新权限勾选
     */
    @Transactional
    public RoleResponse updatePermissions(Long id, List<String> permissions) {
        SysRole role = getExisting(id);
        List<String> saved = replacePermissions(id, permissions);
        userPermissionService.evictByRole(id);
        return toResponse(role, saved);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = getExisting(id);
        if (Integer.valueOf(1).equals(role.getBuiltin())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置角色不可删除");
        }
        Long assigned = userRoleMapper.selectCount(
            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        if (assigned != null && assigned > 0) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "该角色仍有管理员在用，请先解除分配");
        }
        rolePermissionMapper.delete(
            new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, id));
        roleMapper.deleteById(id);
    }

    // ========== 内部方法 ==========

    /**
     * 全量替换角色权限勾选，返回落库后的权限码
     */
    private List<String> replacePermissions(Long roleId, List<String> requested) {
        if (requested == null) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>(requested);
        // 陌生权限码直接拒绝，避免前端写入永远不会生效的脏数据
        List<String> unknown = distinct.stream().filter(code -> !PermissionCode.exists(code)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "存在未登记的权限码：" + String.join("、", unknown));
        }

        rolePermissionMapper.delete(
            new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        List<String> ordered = PermissionCode.retainKnown(distinct);
        for (String code : ordered) {
            SysRolePermission permission = new SysRolePermission();
            permission.setRoleId(roleId);
            permission.setPermissionCode(code);
            permission.setCreatedAt(LocalDateTime.now());
            rolePermissionMapper.insert(permission);
        }
        return ordered;
    }

    private List<String> permissionsOfRole(Long roleId) {
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId))
            .stream()
            .map(SysRolePermission::getPermissionCode)
            .toList();
    }

    private SysRole getExisting(Long id) {
        SysRole role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    private void requireCodeAvailable(String code, Long excludeId) {
        LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code.trim());
        if (excludeId != null) {
            query.ne(SysRole::getId, excludeId);
        }
        Long count = roleMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_ALREADY_EXISTS, "角色标识已被占用");
        }
    }

    private RoleResponse toResponse(SysRole role, List<String> permissions) {
        RoleResponse response = new RoleResponse();
        response.setId(role.getId());
        response.setCode(role.getCode());
        response.setName(role.getName());
        response.setDescription(role.getDescription());
        response.setBuiltin(Integer.valueOf(1).equals(role.getBuiltin()));
        response.setStatus(role.getStatus());
        response.setPermissions(permissions);
        response.setCreatedAt(role.getCreatedAt());
        return response;
    }
}

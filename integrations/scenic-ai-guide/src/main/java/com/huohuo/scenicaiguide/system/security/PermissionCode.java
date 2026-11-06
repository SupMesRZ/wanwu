package com.huohuo.scenicaiguide.system.security;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 后台权限码
 * <p>
 * 权限码固定在代码侧定义，不做成可增删的数据表。理由：没有任何接口校验的权限码是空壳，
 * 允许管理员自由新建只会制造"勾了但不生效"的错觉。角色和角色的权限勾选才是数据
 * <p>
 * 新增权限时：在此登记 → 在对应接口标注 {@link RequirePermission} → 内置角色按需补勾
 */
public enum PermissionCode {

    SCENIC_READ("scenic:read", "景区资产", "查看景区、景点、设施、地图草稿"),
    SCENIC_WRITE("scenic:write", "景区资产", "编辑景区、景点、设施、地图草稿"),
    MAP_PUBLISH("scenic:map:publish", "景区资产", "发布手绘地图版本"),

    QA_READ("qa:read", "智能问答", "查看知识库、FAQ、讲解词"),
    QA_WRITE("qa:write", "智能问答", "编辑知识库、FAQ、讲解词"),
    QA_REINDEX("qa:reindex", "智能问答", "触发向量重建"),

    ROUTE_READ("route:read", "路线导览", "查看预制路线与 AI 路线"),
    ROUTE_WRITE("route:write", "路线导览", "编辑路线与算路策略"),

    DH_READ("dh:read", "数字人", "查看数字人形象、声音、TTS 缓存"),
    DH_WRITE("dh:write", "数字人", "编辑数字人配置、清理 TTS 缓存"),

    TOURIST_READ("tourist:read", "游客运营", "查看游客会话、轨迹、围栏日志"),

    ANALYTICS_READ("analytics:read", "运营分析", "查看运营看板与统计报表"),

    CONFIG_READ("system:config:read", "系统管理", "查看系统配置"),
    CONFIG_WRITE("system:config:write", "系统管理", "修改系统配置"),
    USER_MANAGE("system:user:manage", "系统管理", "查看后台账号（增删改仍限 root）"),
    ROLE_MANAGE("system:role:manage", "系统管理", "查看角色与权限（增删改仍限 root）"),
    LOG_READ("system:log:read", "系统管理", "查看操作日志（清理仍限 root）");

    private final String code;
    private final String module;
    private final String description;

    PermissionCode(String code, String module, String description) {
        this.code = code;
        this.module = module;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getModule() {
        return module;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 校验权限码是否已登记，用于拦截前端传来的陌生权限码
     */
    public static boolean exists(String code) {
        return Arrays.stream(values()).anyMatch(item -> item.code.equals(code));
    }

    /**
     * 过滤出已登记的权限码，顺序按枚举声明顺序归一，便于比对和展示
     */
    public static List<String> retainKnown(Set<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(values())
            .map(PermissionCode::getCode)
            .filter(codes::contains)
            .toList();
    }

    public static Set<String> allCodes() {
        return Arrays.stream(values())
            .map(PermissionCode::getCode)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 按模块分组，供后台权限勾选界面渲染
     */
    public static Map<String, List<PermissionCode>> groupByModule() {
        Map<String, List<PermissionCode>> grouped = new LinkedHashMap<>();
        for (PermissionCode item : values()) {
            grouped.computeIfAbsent(item.module, key -> new java.util.ArrayList<>()).add(item);
        }
        return grouped;
    }
}

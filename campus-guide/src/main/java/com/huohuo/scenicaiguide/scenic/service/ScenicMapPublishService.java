package com.huohuo.scenicaiguide.scenic.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.MapPublishCheckResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicMapDraftResponse;
import com.huohuo.scenicaiguide.scenic.entity.ScenicMapDraft;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicMapDraftMapper;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ScenicMapPublishService {

    private final ScenicMapDraftMapper scenicMapDraftMapper;
    private final ScenicAdminService scenicAdminService;
    private final ScenicTopologyPathService topologyPathService;
    private final ConfigService configService;

    public ScenicMapPublishService(ScenicMapDraftMapper scenicMapDraftMapper,
                                   ScenicAdminService scenicAdminService,
                                   ScenicTopologyPathService topologyPathService,
                                   ConfigService configService) {
        this.scenicMapDraftMapper = scenicMapDraftMapper;
        this.scenicAdminService = scenicAdminService;
        this.topologyPathService = topologyPathService;
        this.configService = configService;
    }

    @Transactional(readOnly = true)
    public MapPublishCheckResponse check(Long draftId) {
        ScenicMapDraft draft = getDraft(draftId);
        MapWorkbenchDraftRequest payload = scenicAdminService.getMapDraftWorkbench(draftId).getPayload();
        MapPublishCheckResponse response = new MapPublishCheckResponse();
        response.setDraftId(draft.getId());
        response.setDraftName(draft.getName());
        response.setStatus(draft.getStatus());
        response.setCheckedAt(LocalDateTime.now());
        response.setSpotCount(payload.getSpots().size());
        response.setFacilityCount(payload.getFacilities().size());
        response.setRouteSegmentCount(payload.getRouteSegments().size());
        response.setPresetRouteCount(payload.getPresetRoutes().size());
        response.setEnabledPresetRouteCount((int) payload.getPresetRoutes().stream().filter(this::enabled).count());

        checkMapBase(payload, response);
        checkSpots(payload, response);
        checkFacilities(payload, response);
        checkRouteSegments(payload, response);
        checkPresetRoutes(payload, response);

        int blockers = (int) response.getIssues().stream().filter(issue -> "ERROR".equals(issue.getLevel())).count();
        int warnings = (int) response.getIssues().stream().filter(issue -> "WARN".equals(issue.getLevel())).count();
        response.setBlockerCount(blockers);
        response.setWarningCount(warnings);
        response.setPublishable(blockers == 0);
        response.setScore(Math.max(0, 100 - blockers * 25 - warnings * 5));
        return response;
    }

    @Transactional
    public ScenicMapDraftResponse publish(Long draftId) {
        MapPublishCheckResponse check = check(draftId);
        if (!Boolean.TRUE.equals(check.getPublishable())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "地图发布校验未通过，请先处理阻塞项");
        }
        return scenicAdminService.publishMapDraftLite(draftId);
    }

    private void checkMapBase(MapWorkbenchDraftRequest payload, MapPublishCheckResponse response) {
        if (!validPoint(payload.getMapCenter())) {
            addIssue(response, "ERROR", "MAP_CENTER_MISSING", "地图中心点缺失", "用户端无法稳定定位默认视野");
        }
        if (payload.getDefaultZoom() == null || payload.getDefaultZoom() < 3) {
            addIssue(response, "WARN", "MAP_ZOOM_WEAK", "默认缩放未配置", "建议在地图工作台设置适合用户端查看的缩放级别");
        }
    }

    private void checkSpots(MapWorkbenchDraftRequest payload, MapPublishCheckResponse response) {
        List<MapWorkbenchDraftRequest.SpotDraft> enabledSpots = payload.getSpots().stream()
            .filter(this::enabled)
            .toList();
        if (enabledSpots.isEmpty()) {
            addIssue(response, "ERROR", "SPOT_EMPTY", "缺少启用地点", "至少需要一个启用地点供校园导航和围栏触发");
            return;
        }
        for (MapWorkbenchDraftRequest.SpotDraft spot : enabledSpots) {
            if (blank(spot.getId()) || blank(spot.getName())) {
                addIssue(response, "ERROR", "SPOT_BASIC_INVALID", "地点基础信息不完整", "启用地点必须有 ID 和名称");
            }
            boolean circle = "圆形".equals(spot.getFenceType()) || "CIRCLE".equalsIgnoreCase(spot.getFenceType());
            if (circle) {
                if (!validSpotPoint(spot) || spot.getRadius() == null || spot.getRadius() < 5) {
                    addIssue(response, "ERROR", "SPOT_CIRCLE_INVALID", "圆形围栏不完整", spot.getName() + " 缺少中心点或有效半径");
                }
            } else if (!validPolygon(spot.getPolygon())) {
                addIssue(response, "ERROR", "SPOT_POLYGON_INVALID", "地点围栏不完整", spot.getName() + " 缺少至少 3 个有效围栏顶点");
            }
        }
    }

    private void checkFacilities(MapWorkbenchDraftRequest payload, MapPublishCheckResponse response) {
        for (MapWorkbenchDraftRequest.FacilityDraft facility : payload.getFacilities()) {
            if (!enabled(facility)) {
                continue;
            }
            if (blank(facility.getName()) || blank(facility.getFacilityType())) {
                addIssue(response, "WARN", "FACILITY_BASIC_WEAK", "服务点信息不完整", "启用服务点建议配置名称和类型");
            }
            if (!validFacilityPoint(facility)) {
                addIssue(response, "WARN", "FACILITY_POINT_MISSING", "服务点坐标缺失", safeName(facility.getName()) + " 不会影响发布，但用户端无法定位");
            }
        }
    }

    private void checkRouteSegments(MapWorkbenchDraftRequest payload, MapPublishCheckResponse response) {
        long enabledSegments = payload.getRouteSegments().stream().filter(this::enabled).count();
        if (enabledSegments == 0) {
            addIssue(response, "ERROR", "ROUTE_SEGMENT_EMPTY", "缺少启用路段", "AI 路线和预置路线需要基于已发布路段图谱");
            return;
        }
        for (MapWorkbenchDraftRequest.RouteDraft route : payload.getRouteSegments()) {
            if (!enabled(route)) {
                continue;
            }
            if (!validEndpoint(route.getFrom()) || !validEndpoint(route.getTo())) {
                addIssue(response, "ERROR", "ROUTE_ENDPOINT_INVALID", "路段端点缺失", safeName(route.getName()) + " 缺少起点或终点");
            }
            if (route.getLengthMeter() == null || route.getLengthMeter() <= 0 || route.getDurationMin() == null || route.getDurationMin() <= 0) {
                addIssue(response, "WARN", "ROUTE_META_WEAK", "路段运营参数不完整", safeName(route.getName()) + " 建议配置长度和耗时");
            }
        }
    }

    private void checkPresetRoutes(MapWorkbenchDraftRequest payload, MapPublishCheckResponse response) {
        List<MapWorkbenchDraftRequest.PresetRouteDraft> enabledPresets = payload.getPresetRoutes().stream()
            .filter(this::enabled)
            .toList();
        if (enabledPresets.isEmpty()) {
            addIssue(response, "ERROR", "PRESET_ROUTE_EMPTY", "缺少启用预置路线", "AI 个性化路线必须基于启用预置路线素材组合");
            return;
        }
        Set<String> spotIds = new HashSet<>();
        for (MapWorkbenchDraftRequest.SpotDraft spot : payload.getSpots()) {
            if (enabled(spot) && !blank(spot.getId())) {
                spotIds.add(spot.getId());
            }
        }
        for (MapWorkbenchDraftRequest.PresetRouteDraft preset : enabledPresets) {
            if (blank(preset.getName())) {
                addIssue(response, "ERROR", "PRESET_NAME_MISSING", "预置路线名称缺失", "启用预置路线必须有名称");
            }
            if (preset.getSpotIds() == null || preset.getSpotIds().size() < 2) {
                addIssue(response, "ERROR", "PRESET_SPOT_TOO_FEW", "预置路线地点不足", safeName(preset.getName()) + " 至少需要两个地点");
                continue;
            }
            int maxTargets = maxPresetTargets();
            if (preset.getSpotIds().size() > maxTargets) {
                // 策略上限只告警不阻断：超限多半是路线确实偏长，拦发布解决不了问题
                addIssue(response, "WARN", "PRESET_SPOT_TOO_MANY", "预置路线地点过多",
                    safeName(preset.getName()) + " 共 " + preset.getSpotIds().size()
                        + " 个地点，超过策略上限 " + maxTargets);
            }
            for (String spotId : preset.getSpotIds()) {
                if (!spotIds.contains(spotId)) {
                    addIssue(response, "ERROR", "PRESET_SPOT_MISSING", "预置路线引用失效", safeName(preset.getName()) + " 引用了不存在或停用的地点 " + spotId);
                }
            }
            for (int i = 0; i < preset.getSpotIds().size() - 1; i++) {
                String from = topologyPathService.spotKey(preset.getSpotIds().get(i));
                String to = topologyPathService.spotKey(preset.getSpotIds().get(i + 1));
                if (!topologyPathService.reachable(payload, from, to)) {
                    addIssue(response, "ERROR", "PRESET_ROUTE_UNREACHABLE", "预置路线不可达", safeName(preset.getName()) + " 中相邻地点无法通过路段连通");
                    break;
                }
            }
        }
    }

    private int maxPresetTargets() {
        try {
            return Math.max(2, configService.getInt("route-max-targets", 15));
        } catch (Exception e) {
            // 配置读不到按默认上限走，不能因此中断发布校验
            return 15;
        }
    }

    private ScenicMapDraft getDraft(Long draftId) {
        ScenicMapDraft draft = scenicMapDraftMapper.selectOne(new QueryWrapper<ScenicMapDraft>()
            .eq("id", draftId)
            .last("LIMIT 1"));
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地图草稿不存在");
        }
        return draft;
    }

    private boolean enabled(MapWorkbenchDraftRequest.SpotDraft item) {
        return item == null || item.getStatus() == null || item.getStatus() == 1;
    }

    private boolean enabled(MapWorkbenchDraftRequest.FacilityDraft item) {
        return item == null || item.getStatus() == null || item.getStatus() == 1;
    }

    private boolean enabled(MapWorkbenchDraftRequest.RouteDraft item) {
        return item == null || item.getStatus() == null || item.getStatus() == 1;
    }

    private boolean enabled(MapWorkbenchDraftRequest.PresetRouteDraft item) {
        return item == null || item.getStatus() == null || item.getStatus() == 1;
    }

    private boolean validEndpoint(MapWorkbenchDraftRequest.EndpointRefDraft ref) {
        return ref != null && !blank(ref.getType()) && !blank(ref.getId());
    }

    private boolean validSpotPoint(MapWorkbenchDraftRequest.SpotDraft spot) {
        return validPoint(spot.getLabelPoint()) || spot.getLatitude() != null && spot.getLongitude() != null;
    }

    private boolean validFacilityPoint(MapWorkbenchDraftRequest.FacilityDraft facility) {
        return validPoint(facility.getPoint()) || facility.getLatitude() != null && facility.getLongitude() != null;
    }

    private boolean validPolygon(List<List<BigDecimal>> polygon) {
        return polygon != null && polygon.size() >= 3 && polygon.stream().allMatch(this::validPoint);
    }

    private boolean validPoint(List<BigDecimal> point) {
        return point != null && point.size() >= 2 && point.get(0) != null && point.get(1) != null;
    }

    private void addIssue(MapPublishCheckResponse response, String level, String code, String title, String detail) {
        MapPublishCheckResponse.Issue issue = new MapPublishCheckResponse.Issue();
        issue.setLevel(level);
        issue.setCode(code);
        issue.setTitle(title);
        issue.setDetail(detail);
        response.getIssues().add(issue);
    }

    private String safeName(String value) {
        return blank(value) ? "未命名对象" : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

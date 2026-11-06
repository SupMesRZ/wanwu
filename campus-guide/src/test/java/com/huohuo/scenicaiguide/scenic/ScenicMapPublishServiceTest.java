package com.huohuo.scenicaiguide.scenic;

import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.MapPublishCheckResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.MapWorkbenchResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicMapDraftResponse;
import com.huohuo.scenicaiguide.scenic.entity.ScenicMapDraft;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicMapDraftMapper;
import com.huohuo.scenicaiguide.scenic.service.ScenicAdminService;
import com.huohuo.scenicaiguide.scenic.service.ScenicMapPublishService;
import com.huohuo.scenicaiguide.scenic.service.ScenicTopologyPathService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScenicMapPublishServiceTest {

    @Test
    void marksValidDraftAsPublishable() {
        ScenicMapDraftMapper draftMapper = mock(ScenicMapDraftMapper.class);
        ScenicAdminService adminService = mock(ScenicAdminService.class);
        ScenicMapDraft draft = draft();
        MapWorkbenchDraftRequest payload = validPayload(true);
        when(draftMapper.selectOne(any())).thenReturn(draft);
        when(adminService.getMapDraftWorkbench(10L)).thenReturn(workbench(payload));

        ScenicMapPublishService service = new ScenicMapPublishService(draftMapper, adminService, new ScenicTopologyPathService(), mock(ConfigService.class));

        MapPublishCheckResponse response = service.check(10L);

        assertThat(response.getPublishable()).isTrue();
        assertThat(response.getBlockerCount()).isZero();
        assertThat(response.getEnabledPresetRouteCount()).isEqualTo(1);
    }

    @Test
    void rejectsPublishWhenPresetRouteIsUnreachable() {
        ScenicMapDraftMapper draftMapper = mock(ScenicMapDraftMapper.class);
        ScenicAdminService adminService = mock(ScenicAdminService.class);
        ScenicMapDraft draft = draft();
        MapWorkbenchDraftRequest payload = validPayload(false);
        when(draftMapper.selectOne(any())).thenReturn(draft);
        when(adminService.getMapDraftWorkbench(10L)).thenReturn(workbench(payload));

        ScenicMapPublishService service = new ScenicMapPublishService(draftMapper, adminService, new ScenicTopologyPathService(), mock(ConfigService.class));

        assertThat(service.check(10L).getIssues())
            .anyMatch(issue -> "PRESET_ROUTE_UNREACHABLE".equals(issue.getCode()));
        assertThatThrownBy(() -> service.publish(10L))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("发布校验未通过");
    }

    @Test
    void warnsWhenPresetExceedsMaxTargets() {
        ScenicMapDraftMapper draftMapper = mock(ScenicMapDraftMapper.class);
        ScenicAdminService adminService = mock(ScenicAdminService.class);
        ConfigService configService = mock(ConfigService.class);
        MapWorkbenchDraftRequest payload = validPayload(true);
        when(draftMapper.selectOne(any())).thenReturn(draft());
        when(adminService.getMapDraftWorkbench(10L)).thenReturn(workbench(payload));
        // 上限配成 2，实际 2 个景点不警告；配成 1 时按下限 2 兜底也不警告
        when(configService.getInt("route-max-targets", 15)).thenReturn(2);
        ScenicMapPublishService service = new ScenicMapPublishService(
            draftMapper, adminService, new ScenicTopologyPathService(), configService);

        MapPublishCheckResponse within = service.check(10L);
        assertThat(within.getIssues()).noneMatch(issue -> "PRESET_SPOT_TOO_MANY".equals(issue.getCode()));

        when(configService.getInt("route-max-targets", 15)).thenReturn(1);
        MapPublishCheckResponse clamped = service.check(10L);
        assertThat(clamped.getIssues()).noneMatch(issue -> "PRESET_SPOT_TOO_MANY".equals(issue.getCode()));

        // 构造 3 个连通景点的路线，上限 2 → 警告但不阻断发布
        MapWorkbenchDraftRequest bigger = validPayload(true);
        bigger.setSpots(List.of(spot("s1", "入口"), spot("s2", "书院"), spot("s3", "茶亭")));
        bigger.setRouteSegments(List.of(route("r1", "s1", "s2"), route("r2", "s2", "s3")));
        bigger.getPresetRoutes().getFirst().setSpotIds(List.of("s1", "s2", "s3"));
        when(adminService.getMapDraftWorkbench(10L)).thenReturn(workbench(bigger));
        when(configService.getInt("route-max-targets", 15)).thenReturn(2);

        MapPublishCheckResponse exceeded = service.check(10L);
        assertThat(exceeded.getIssues())
            .anyMatch(issue -> "PRESET_SPOT_TOO_MANY".equals(issue.getCode())
                && "WARN".equals(issue.getLevel()));
        assertThat(exceeded.getPublishable()).isTrue();
    }

    private ScenicMapDraft draft() {
        ScenicMapDraft draft = new ScenicMapDraft();
        draft.setId(10L);
        draft.setScenicAreaId(1L);
        draft.setName("测试地图");
        draft.setStatus("DRAFT");
        return draft;
    }

    private MapWorkbenchResponse workbench(MapWorkbenchDraftRequest payload) {
        ScenicMapDraftResponse draft = new ScenicMapDraftResponse();
        draft.setId(10L);
        draft.setName("测试地图");
        draft.setStatus("DRAFT");
        MapWorkbenchResponse response = new MapWorkbenchResponse();
        response.setCurrentDraft(draft);
        response.setPayload(payload);
        return response;
    }

    private MapWorkbenchDraftRequest validPayload(boolean connected) {
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        payload.setMapCenter(List.of(BigDecimal.valueOf(30.1), BigDecimal.valueOf(120.1)));
        payload.setDefaultZoom(16);
        payload.setSpots(List.of(spot("s1", "入口"), spot("s2", "书院")));
        if (connected) {
            payload.setRouteSegments(List.of(route("r1", "s1", "s2")));
        }
        MapWorkbenchDraftRequest.PresetRouteDraft preset = new MapWorkbenchDraftRequest.PresetRouteDraft();
        preset.setId("p1");
        preset.setName("经典路线");
        preset.setSpotIds(List.of("s1", "s2"));
        preset.setStatus(1);
        payload.setPresetRoutes(List.of(preset));
        return payload;
    }

    private MapWorkbenchDraftRequest.SpotDraft spot(String id, String name) {
        MapWorkbenchDraftRequest.SpotDraft spot = new MapWorkbenchDraftRequest.SpotDraft();
        spot.setId(id);
        spot.setName(name);
        spot.setFenceType("圆形");
        spot.setLabelPoint(List.of(BigDecimal.valueOf(30.1), BigDecimal.valueOf(120.1)));
        spot.setRadius(50);
        spot.setStatus(1);
        return spot;
    }

    private MapWorkbenchDraftRequest.RouteDraft route(String id, String fromSpotId, String toSpotId) {
        MapWorkbenchDraftRequest.RouteDraft route = new MapWorkbenchDraftRequest.RouteDraft();
        route.setId(id);
        route.setName("连通路段");
        route.setFrom(endpoint(fromSpotId));
        route.setTo(endpoint(toSpotId));
        route.setLengthMeter(100);
        route.setDurationMin(3);
        route.setStatus(1);
        return route;
    }

    private MapWorkbenchDraftRequest.EndpointRefDraft endpoint(String spotId) {
        MapWorkbenchDraftRequest.EndpointRefDraft endpoint = new MapWorkbenchDraftRequest.EndpointRefDraft();
        endpoint.setType("SPOT");
        endpoint.setId(spotId);
        return endpoint;
    }
}

package com.huohuo.scenicaiguide.scenic.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicAreaRequest;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicFacilityRequest;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicMapDraftRequest;
import com.huohuo.scenicaiguide.scenic.dto.request.ScenicSpotRequest;
import com.huohuo.scenicaiguide.scenic.dto.response.MapWorkbenchResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicAreaResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicFacilityResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicMapDraftResponse;
import com.huohuo.scenicaiguide.scenic.dto.response.ScenicSpotResponse;
import com.huohuo.scenicaiguide.scenic.entity.ScenicArea;
import com.huohuo.scenicaiguide.scenic.entity.ScenicFacility;
import com.huohuo.scenicaiguide.scenic.entity.ScenicMapDraft;
import com.huohuo.scenicaiguide.scenic.entity.ScenicSpot;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicAreaMapper;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicFacilityMapper;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicMapDraftMapper;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicSpotMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class ScenicAdminService {

    public static final String DRAFT_STATUS = "DRAFT";
    public static final String ONLINE_STATUS = "ONLINE";
    public static final String DEFAULT_AREA_NAME = "河北大学七一路校区";
    private static final String LEGACY_DEFAULT_AREA_NAME = "桃花源";

    private final ScenicAreaMapper scenicAreaMapper;
    private final ScenicSpotMapper scenicSpotMapper;
    private final ScenicFacilityMapper scenicFacilityMapper;
    private final ScenicMapDraftMapper scenicMapDraftMapper;
    private final ObjectMapper objectMapper;

    @Autowired
    public ScenicAdminService(ScenicAreaMapper scenicAreaMapper,
                              ScenicSpotMapper scenicSpotMapper,
                              ScenicFacilityMapper scenicFacilityMapper,
                              ScenicMapDraftMapper scenicMapDraftMapper,
                              ObjectMapper objectMapper) {
        this.scenicAreaMapper = scenicAreaMapper;
        this.scenicSpotMapper = scenicSpotMapper;
        this.scenicFacilityMapper = scenicFacilityMapper;
        this.scenicMapDraftMapper = scenicMapDraftMapper;
        this.objectMapper = objectMapper;
    }

    public List<ScenicAreaResponse> listAreas() {
        getOrCreateDefaultAreaEntity();
        return scenicAreaMapper.selectList(new QueryWrapper<ScenicArea>().orderByAsc("id"))
            .stream()
            .map(ScenicAreaResponse::from)
            .toList();
    }

    public ScenicAreaResponse getArea(Long areaId) {
        return ScenicAreaResponse.from(getAreaEntity(areaId));
    }

    public ScenicAreaResponse createArea(ScenicAreaRequest request) {
        ScenicArea entity = new ScenicArea();
        fillArea(entity, request);
        scenicAreaMapper.insert(entity);
        return ScenicAreaResponse.from(entity);
    }

    public ScenicAreaResponse updateArea(Long areaId, ScenicAreaRequest request) {
        ScenicArea entity = getAreaEntity(areaId);
        fillArea(entity, request);
        scenicAreaMapper.updateById(entity);
        return ScenicAreaResponse.from(entity);
    }

    @Transactional
    public void deleteArea(Long areaId) {
        getAreaEntity(areaId);
        scenicAreaMapper.deleteById(areaId);
    }

    public List<ScenicSpotResponse> listSpots(Long areaId) {
        getAreaEntity(areaId);
        return scenicSpotMapper.selectList(new QueryWrapper<ScenicSpot>()
                .eq("scenic_area_id", areaId)
                .orderByAsc("id"))
            .stream()
            .map(ScenicSpotResponse::from)
            .toList();
    }

    public ScenicSpotResponse createSpot(Long areaId, ScenicSpotRequest request) {
        getAreaEntity(areaId);
        ScenicSpot entity = new ScenicSpot();
        fillSpot(entity, areaId, request);
        scenicSpotMapper.insert(entity);
        return ScenicSpotResponse.from(entity);
    }

    public ScenicSpotResponse updateSpot(Long spotId, ScenicSpotRequest request) {
        ScenicSpot entity = getSpotEntity(spotId);
        fillSpot(entity, entity.getScenicAreaId(), request);
        scenicSpotMapper.updateById(entity);
        return ScenicSpotResponse.from(entity);
    }

    @Transactional
    public void deleteSpot(Long spotId) {
        ScenicSpot entity = getSpotEntity(spotId);
        scenicSpotMapper.deleteById(spotId);
    }

    public List<ScenicFacilityResponse> listFacilities(Long areaId) {
        getAreaEntity(areaId);
        return scenicFacilityMapper.selectList(new QueryWrapper<ScenicFacility>()
                .eq("scenic_area_id", areaId)
                .orderByAsc("id"))
            .stream()
            .map(ScenicFacilityResponse::from)
            .toList();
    }

    public ScenicFacilityResponse createFacility(Long areaId, ScenicFacilityRequest request) {
        getAreaEntity(areaId);
        ScenicFacility entity = new ScenicFacility();
        fillFacility(entity, areaId, request);
        scenicFacilityMapper.insert(entity);
        return ScenicFacilityResponse.from(entity);
    }

    public ScenicFacilityResponse updateFacility(Long facilityId, ScenicFacilityRequest request) {
        ScenicFacility entity = getFacilityEntity(facilityId);
        fillFacility(entity, entity.getScenicAreaId(), request);
        scenicFacilityMapper.updateById(entity);
        return ScenicFacilityResponse.from(entity);
    }

    @Transactional
    public void deleteFacility(Long facilityId) {
        ScenicFacility entity = getFacilityEntity(facilityId);
        scenicFacilityMapper.deleteById(entity.getId());
    }

    public List<ScenicMapDraftResponse> listMapDrafts(Long areaId) {
        getAreaEntity(areaId);
        return listDraftEntities(areaId).stream()
            .map(ScenicMapDraftResponse::from)
            .toList();
    }

    public List<ScenicMapDraftResponse> listMapDraftsWithDefaultFallback(Long areaId) {
        ScenicArea area = getAreaEntityOrDefault(areaId);
        return listDraftEntities(area.getId()).stream()
            .map(ScenicMapDraftResponse::from)
            .toList();
    }

    public List<ScenicMapDraftResponse> listDefaultMapDrafts() {
        ScenicArea area = getOrCreateDefaultAreaEntity();
        return listMapDrafts(area.getId());
    }

    @Transactional
    public ScenicMapDraftResponse createMapDraft(Long areaId, ScenicMapDraftRequest request) {
        getAreaEntity(areaId);
        ScenicMapDraft draft = new ScenicMapDraft();
        draft.setScenicAreaId(areaId);
        draft.setName(defaultText(request == null ? null : request.getName(), "新地图草稿"));
        draft.setStatus(DRAFT_STATUS);
        draft.setPayload(writeJson(resolveInitialPayload(areaId, request == null ? null : request.getCopyFromDraftId())));
        draft.setLastSavedAt(LocalDateTime.now());
        draft.setCreatedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        draft.setDeleted(0);
        scenicMapDraftMapper.insert(draft);
        return ScenicMapDraftResponse.from(draft);
    }

    @Transactional
    public ScenicMapDraftResponse createMapDraftWithDefaultFallback(Long areaId, ScenicMapDraftRequest request) {
        ScenicArea area = getAreaEntityOrDefault(areaId);
        return createMapDraft(area.getId(), request);
    }

    @Transactional
    public ScenicMapDraftResponse createDefaultMapDraft(ScenicMapDraftRequest request) {
        ScenicArea area = getOrCreateDefaultAreaEntity();
        return createMapDraft(area.getId(), request);
    }

    public MapWorkbenchResponse getMapDraftWorkbench(Long draftId) {
        ScenicMapDraft draft = getDraftEntity(draftId);
        return buildWorkbenchResponse(draft);
    }

    @Transactional
    public MapWorkbenchResponse saveMapDraftWorkbench(Long draftId, MapWorkbenchDraftRequest request) {
        ScenicMapDraft draft = getDraftEntity(draftId);
        MapWorkbenchDraftRequest incoming = normalizePayload(request);
        MapWorkbenchDraftRequest current = readPayload(draft.getPayload());
        if (ONLINE_STATUS.equals(draft.getStatus())) {
            if (topologyChanged(current, incoming)) {
                throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "线上草稿禁止修改物理拓扑");
            }
        }
        draft.setPayload(writeJson(incoming));
        draft.setLastSavedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        scenicMapDraftMapper.updateById(draft);
        return buildWorkbenchResponse(draft);
    }

    @Transactional
    public MapWorkbenchResponse savePresetRoutes(Long draftId, List<MapWorkbenchDraftRequest.PresetRouteDraft> presetRoutes) {
        ScenicMapDraft draft = getDraftEntity(draftId);
        MapWorkbenchDraftRequest current = readPayload(draft.getPayload());
        List<MapWorkbenchDraftRequest.PresetRouteDraft> nextRoutes = new ArrayList<>();
        if (presetRoutes != null) {
            for (MapWorkbenchDraftRequest.PresetRouteDraft route : presetRoutes) {
                if (route != null) {
                    nextRoutes.add(normalizePresetRoute(route));
                }
            }
        }
        current.setPresetRoutes(nextRoutes);
        draft.setPayload(writeJson(current));
        draft.setLastSavedAt(LocalDateTime.now());
        draft.setUpdatedAt(LocalDateTime.now());
        scenicMapDraftMapper.updateById(draft);
        return buildWorkbenchResponse(draft);
    }

    private MapWorkbenchDraftRequest.PresetRouteDraft normalizePresetRoute(MapWorkbenchDraftRequest.PresetRouteDraft route) {
        MapWorkbenchDraftRequest.PresetRouteDraft normalized = new MapWorkbenchDraftRequest.PresetRouteDraft();
        normalized.setId(defaultText(route.getId(), createMapObjectId()));
        normalized.setName(defaultText(route.getName(), "未命名预置路线"));
        normalized.setSpotIds(route.getSpotIds() == null ? new ArrayList<>() : new ArrayList<>(route.getSpotIds()));
        normalized.setSegmentIds(route.getSegmentIds() == null ? new ArrayList<>() : new ArrayList<>(route.getSegmentIds()));
        normalized.setDescription(defaultText(route.getDescription(), ""));
        normalized.setDurationMin(route.getDurationMin() == null ? 30 : route.getDurationMin());
        normalized.setAudience(defaultText(route.getAudience(), ""));
        normalized.setRecommendationNote(defaultText(route.getRecommendationNote(), ""));
        normalized.setStatus(route.getStatus() == null ? 1 : route.getStatus());
        return normalized;
    }

    @Transactional
    public void deleteMapDraft(Long draftId) {
        ScenicMapDraft draft = getDraftEntity(draftId);
        if (ONLINE_STATUS.equals(draft.getStatus())) {
            throw new BusinessException(ErrorCode.OPERATION_NOT_ALLOWED, "线上草稿不能删除");
        }
        scenicMapDraftMapper.deleteById(draft.getId());
    }

    @Transactional
    public ScenicMapDraftResponse publishMapDraftLite(Long draftId) {
        ScenicMapDraft draft = getDraftEntity(draftId);
        if (ONLINE_STATUS.equals(draft.getStatus())) {
            return ScenicMapDraftResponse.from(draft);
        }

        ScenicMapDraft oldOnline = getOnlineDraft(draft.getScenicAreaId());
        if (oldOnline != null) {
            oldOnline.setStatus(DRAFT_STATUS);
            oldOnline.setUpdatedAt(LocalDateTime.now());
            scenicMapDraftMapper.updateById(oldOnline);
        }

        draft.setStatus(ONLINE_STATUS);
        draft.setUpdatedAt(LocalDateTime.now());
        scenicMapDraftMapper.updateById(draft);
        return ScenicMapDraftResponse.from(draft);
    }

    public MapWorkbenchResponse getOnlineTouristMap(Long areaId) {
        ScenicArea area = areaId == null ? getOrCreateDefaultAreaEntity() : getAreaEntity(areaId);
        ScenicMapDraft draft = getOnlineDraft(area.getId());
        if (draft == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "校园暂无上线地图");
        }
        MapWorkbenchResponse response = new MapWorkbenchResponse();
        response.setScenicAreas(List.of());
        response.setCurrentArea(getArea(draft.getScenicAreaId()));
        response.setCurrentDraft(ScenicMapDraftResponse.from(draft));
        response.setDrafts(List.of());
        response.setPayload(readPayload(draft.getPayload()));
        return response;
    }

    private MapWorkbenchResponse buildWorkbenchResponse(ScenicMapDraft draft) {
        MapWorkbenchResponse response = new MapWorkbenchResponse();
        response.setScenicAreas(listAreas());
        response.setCurrentArea(getArea(draft.getScenicAreaId()));
        response.setCurrentDraft(ScenicMapDraftResponse.from(draft));
        response.setDrafts(listMapDrafts(draft.getScenicAreaId()));
        response.setPayload(readPayload(draft.getPayload()));
        return response;
    }


    private MapWorkbenchDraftRequest resolveInitialPayload(Long areaId, Long copyFromDraftId) {
        if (copyFromDraftId != null) {
            ScenicMapDraft source = getDraftEntity(copyFromDraftId);
            if (!Objects.equals(source.getScenicAreaId(), areaId)) {
                throw new BusinessException(ErrorCode.PARAM_INVALID, "只能复制同校区地图草稿");
            }
            return readPayload(source.getPayload());
        }
        ScenicArea area = getAreaEntity(areaId);
        MapWorkbenchDraftRequest payload = new MapWorkbenchDraftRequest();
        if (area.getCenterLatitude() != null && area.getCenterLongitude() != null) {
            payload.setMapCenter(List.of(area.getCenterLatitude(), area.getCenterLongitude()));
        }
        payload.setDefaultZoom(area.getDefaultZoom());
        return normalizePayload(payload);
    }

    private MapWorkbenchDraftRequest normalizePayload(MapWorkbenchDraftRequest payload) {
        MapWorkbenchDraftRequest result = payload == null ? new MapWorkbenchDraftRequest() : payload;
        if (result.getSpots() == null) {
            result.setSpots(new ArrayList<>());
        }
        if (result.getFacilities() == null) {
            result.setFacilities(new ArrayList<>());
        }
        if (result.getRouteEndpoints() == null) {
            result.setRouteEndpoints(new ArrayList<>());
        }
        if (result.getRouteSegments() == null) {
            result.setRouteSegments(new ArrayList<>());
        }
        if (result.getPresetRoutes() == null) {
            result.setPresetRoutes(new ArrayList<>());
        }
        if (result.getDefaultZoom() == null) {
            result.setDefaultZoom(16);
        }
        return result;
    }

    private boolean topologyChanged(MapWorkbenchDraftRequest current, MapWorkbenchDraftRequest incoming) {
        if (!Objects.equals(pointSignature(current.getMapCenter()), pointSignature(incoming.getMapCenter()))) {
            return true;
        }
        if (!Objects.equals(current.getDefaultZoom(), incoming.getDefaultZoom())) {
            return true;
        }
        return !Objects.equals(topologySignature(current), topologySignature(incoming));
    }

    private String topologySignature(MapWorkbenchDraftRequest payload) {
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("defaultZoom", payload.getDefaultZoom());
        signature.put("spots", payload.getSpots().stream()
            .map(spot -> topologyItem(
                "id", spot.getId(),
                "labelPoint", pointSignature(resolveSpotPoint(spot)),
                "polygon", polygonSignature(spot.getPolygon())))
            .toList());
        signature.put("facilities", payload.getFacilities().stream()
            .map(facility -> topologyItem(
                "id", facility.getId(),
                "point", pointSignature(resolveFacilityPoint(facility))))
            .toList());
        signature.put("routeEndpoints", payload.getRouteEndpoints().stream()
            .map(endpoint -> topologyItem(
                "id", endpoint.getId(),
                "point", pointSignature(resolveEndpointPoint(endpoint))))
            .toList());
        signature.put("routeSegments", payload.getRouteSegments().stream()
            .map(route -> topologyItem(
                "id", route.getId(),
                "from", endpointKey(route.getFrom()),
                "to", endpointKey(route.getTo()),
                "middlePoints", polygonSignature(route.getMiddlePoints())))
            .toList());
        return writeJson(signature);
    }

    private List<String> polygonSignature(List<List<BigDecimal>> points) {
        if (points == null) {
            return List.of();
        }
        return points.stream().map(this::pointSignature).toList();
    }

    private String pointSignature(List<BigDecimal> point) {
        if (!validPoint(point)) {
            return "";
        }
        return coordinateSignature(point.get(0)) + "," + coordinateSignature(point.get(1));
    }

    private String coordinateSignature(BigDecimal value) {
        if (value == null) {
            return "";
        }
        return value.setScale(7, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private Map<String, Object> topologyItem(Object... pairs) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            item.put((String) pairs[i], pairs[i + 1]);
        }
        return item;
    }

    private String endpointKey(MapWorkbenchDraftRequest.EndpointRefDraft ref) {
        if (ref == null) {
            return "NULL";
        }
        return normalizeType(ref.getType()) + ":" + ref.getId();
    }

    private String normalizeType(String type) {
        return type == null ? "ENDPOINT" : type.trim().toUpperCase();
    }

    private List<BigDecimal> resolveSpotPoint(MapWorkbenchDraftRequest.SpotDraft spot) {
        if (validPoint(spot.getLabelPoint())) {
            return spot.getLabelPoint();
        }
        if (spot.getLatitude() != null && spot.getLongitude() != null) {
            return List.of(spot.getLatitude(), spot.getLongitude());
        }
        return List.of();
    }

    private List<BigDecimal> resolveFacilityPoint(MapWorkbenchDraftRequest.FacilityDraft facility) {
        if (validPoint(facility.getPoint())) {
            return facility.getPoint();
        }
        if (facility.getLatitude() != null && facility.getLongitude() != null) {
            return List.of(facility.getLatitude(), facility.getLongitude());
        }
        return List.of();
    }

    private List<BigDecimal> resolveEndpointPoint(MapWorkbenchDraftRequest.EndpointDraft endpoint) {
        if (validPoint(endpoint.getPoint())) {
            return endpoint.getPoint();
        }
        if (endpoint.getLatitude() != null && endpoint.getLongitude() != null) {
            return List.of(endpoint.getLatitude(), endpoint.getLongitude());
        }
        return List.of();
    }

    private boolean validPolygon(List<List<BigDecimal>> polygon) {
        return polygon != null && polygon.size() >= 3 && polygon.stream().allMatch(this::validPoint);
    }

    private boolean validPoint(List<BigDecimal> point) {
        return point != null && point.size() >= 2 && point.get(0) != null && point.get(1) != null;
    }

    private List<ScenicMapDraft> listDraftEntities(Long areaId) {
        return scenicMapDraftMapper.selectList(new QueryWrapper<ScenicMapDraft>()
            .eq("scenic_area_id", areaId)
            .last("ORDER BY CASE WHEN status = 'ONLINE' THEN 0 ELSE 1 END, updated_at DESC"));
    }

    private ScenicMapDraft getOnlineDraft(Long areaId) {
        return scenicMapDraftMapper.selectOne(new QueryWrapper<ScenicMapDraft>()
            .eq("scenic_area_id", areaId)
            .eq("status", ONLINE_STATUS)
            .last("LIMIT 1"));
    }

    private ScenicMapDraft getDraftEntity(Long draftId) {
        ScenicMapDraft entity = scenicMapDraftMapper.selectById(draftId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地图草稿不存在");
        }
        return entity;
    }

    private MapWorkbenchDraftRequest readPayload(String value) {
        if (value == null || value.isBlank()) {
            return normalizePayload(new MapWorkbenchDraftRequest());
        }
        try {
            return normalizePayload(objectMapper.readValue(value, MapWorkbenchDraftRequest.class));
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "地图草稿数据格式错误");
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "地图草稿数据格式错误");
        }
    }

    private void fillArea(ScenicArea entity, ScenicAreaRequest request) {
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        entity.setCenterLongitude(request.getCenterLongitude());
        entity.setCenterLatitude(request.getCenterLatitude());
        entity.setDefaultZoom(request.getDefaultZoom() == null ? 16 : request.getDefaultZoom());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void fillSpot(ScenicSpot entity, Long areaId, ScenicSpotRequest request) {
        entity.setScenicAreaId(areaId);
        entity.setName(request.getName().trim());
        entity.setDescription(request.getDescription());
        entity.setLabelLongitude(request.getLabelLongitude());
        entity.setLabelLatitude(request.getLabelLatitude());
        entity.setFencePolygon(request.getFencePolygon());
        entity.setFenceType(defaultText(request.getFenceType(), "POLYGON").toUpperCase());
        entity.setFenceRadiusMeter(request.getRadius());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void fillFacility(ScenicFacility entity, Long areaId, ScenicFacilityRequest request) {
        entity.setScenicAreaId(areaId);
        entity.setName(request.getName().trim());
        entity.setFacilityType(request.getFacilityType().trim().toUpperCase());
        entity.setDescription(request.getDescription());
        entity.setLongitude(request.getLongitude());
        entity.setLatitude(request.getLatitude());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private ScenicArea getAreaEntity(Long areaId) {
        ScenicArea entity = scenicAreaMapper.selectById(areaId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "校区不存在");
        }
        return entity;
    }

    private ScenicArea getAreaEntityOrDefault(Long areaId) {
        ScenicArea entity = areaId == null ? null : scenicAreaMapper.selectById(areaId);
        return entity == null ? getOrCreateDefaultAreaEntity() : entity;
    }

    @Transactional
    public ScenicAreaResponse getDefaultArea() {
        return ScenicAreaResponse.from(getOrCreateDefaultAreaEntity());
    }

    private ScenicArea getOrCreateDefaultAreaEntity() {
        ScenicArea existing = scenicAreaMapper.selectOne(new QueryWrapper<ScenicArea>()
            .eq("deleted", 0)
            .orderByAsc("id")
            .last("LIMIT 1"));
        if (existing != null) {
            if (LEGACY_DEFAULT_AREA_NAME.equals(existing.getName())) {
                existing.setName(DEFAULT_AREA_NAME);
                existing.setDescription("河北省保定市莲池区七一东路2666号");
                existing.setCenterLongitude(BigDecimal.valueOf(115.5661970));
                existing.setCenterLatitude(BigDecimal.valueOf(38.8819490));
                existing.setDefaultZoom(17);
                existing.setUpdatedAt(LocalDateTime.now());
                scenicAreaMapper.updateById(existing);
            }
            return existing;
        }

        ScenicArea area = new ScenicArea();
        area.setName(DEFAULT_AREA_NAME);
        area.setDescription("河北省保定市莲池区七一东路2666号");
        area.setCenterLongitude(BigDecimal.valueOf(115.5661970));
        area.setCenterLatitude(BigDecimal.valueOf(38.8819490));
        area.setDefaultZoom(17);
        area.setStatus(1);
        area.setCreatedAt(LocalDateTime.now());
        area.setUpdatedAt(LocalDateTime.now());
        area.setDeleted(0);
        scenicAreaMapper.insert(area);
        return area;
    }

    private ScenicSpot getSpotEntity(Long spotId) {
        ScenicSpot entity = scenicSpotMapper.selectById(spotId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地点不存在");
        }
        return entity;
    }

    private ScenicFacility getFacilityEntity(Long facilityId) {
        ScenicFacility entity = scenicFacilityMapper.selectById(facilityId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务点不存在");
        }
        return entity;
    }

    public void ensureSpotInArea(Long areaId, Long spotId) {
        ScenicSpot spot = getSpotEntity(spotId);
        if (!Objects.equals(spot.getScenicAreaId(), areaId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "地点不存在");
        }
    }

    public void ensureFacilityInArea(Long areaId, Long facilityId) {
        ScenicFacility facility = getFacilityEntity(facilityId);
        if (!Objects.equals(facility.getScenicAreaId(), areaId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "服务点不存在");
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String createMapObjectId() {
        return "preset_" + System.currentTimeMillis() + "_" + Integer.toHexString((int) (Math.random() * 0x10000));
    }
}

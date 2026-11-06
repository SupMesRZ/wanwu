package com.huohuo.scenicaiguide.scenic.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.scenic.dto.request.MapWorkbenchDraftRequest;
import com.huohuo.scenicaiguide.scenic.entity.ScenicArea;
import com.huohuo.scenicaiguide.scenic.entity.ScenicMapDraft;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicAreaMapper;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicMapDraftMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class ScenicTopologyService {

    private final ScenicAreaMapper scenicAreaMapper;
    private final ScenicMapDraftMapper scenicMapDraftMapper;
    private final ObjectMapper objectMapper;

    public ScenicTopologyService(ScenicAreaMapper scenicAreaMapper,
                                 ScenicMapDraftMapper scenicMapDraftMapper,
                                 ObjectMapper objectMapper) {
        this.scenicAreaMapper = scenicAreaMapper;
        this.scenicMapDraftMapper = scenicMapDraftMapper;
        this.objectMapper = objectMapper;
    }

    public PublishedTopology getDefaultPublishedTopology() {
        ScenicArea area = scenicAreaMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScenicArea>()
                    .eq("deleted", 0)
                    .orderByAsc("id")
                    .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "暂无校园数据"));
        return getPublishedTopology(area.getId());
    }

    public PublishedTopology getPublishedTopology(Long scenicAreaId) {
        ScenicMapDraft draft = scenicMapDraftMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ScenicMapDraft>()
                    .eq("scenic_area_id", scenicAreaId)
                    .eq("status", ScenicAdminService.ONLINE_STATUS)
                    .orderByDesc("updated_at")
                    .last("LIMIT 1"))
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "校园暂无上线地图"));
        return new PublishedTopology(scenicAreaId, draft.getId(), readPayload(draft.getPayload()));
    }

    public MapWorkbenchDraftRequest.SpotDraft findSpot(PublishedTopology topology, String spotId) {
        return topology.payload().getSpots().stream()
            .filter(spot -> String.valueOf(spot.getId()).equals(String.valueOf(spotId)))
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "地点不存在"));
    }

    public List<MapWorkbenchDraftRequest.SpotDraft> enabledSpots(PublishedTopology topology) {
        return topology.payload().getSpots().stream()
            .filter(spot -> spot.getStatus() == null || spot.getStatus() == 1)
            .toList();
    }

    public MapWorkbenchDraftRequest.SpotDraft nearestSpot(PublishedTopology topology,
                                                          BigDecimal latitude,
                                                          BigDecimal longitude) {
        return enabledSpots(topology).stream()
            .filter(spot -> resolvePoint(spot) != null)
            .min(Comparator.comparingDouble(spot -> distanceMeters(latitude, longitude,
                resolvePoint(spot).get(0), resolvePoint(spot).get(1))))
            .orElse(null);
    }

    public List<BigDecimal> resolvePoint(MapWorkbenchDraftRequest.SpotDraft spot) {
        if (spot.getLabelPoint() != null && spot.getLabelPoint().size() >= 2) {
            return spot.getLabelPoint();
        }
        if (spot.getLatitude() != null && spot.getLongitude() != null) {
            return List.of(spot.getLatitude(), spot.getLongitude());
        }
        return null;
    }

    public double distanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        double earthRadius = 6371000D;
        double radLat1 = Math.toRadians(lat1.doubleValue());
        double radLat2 = Math.toRadians(lat2.doubleValue());
        double deltaLat = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLng = Math.toRadians(lng2.subtract(lng1).doubleValue());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
            + Math.cos(radLat1) * Math.cos(radLat2)
            * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private MapWorkbenchDraftRequest readPayload(String value) {
        try {
            return objectMapper.readValue(value, MapWorkbenchDraftRequest.class);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.PARAM_INVALID, "线上地图数据格式错误");
        }
    }

    public record PublishedTopology(Long scenicAreaId, Long draftId, MapWorkbenchDraftRequest payload) {
    }
}

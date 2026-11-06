package com.huohuo.scenicaiguide.tourist.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.AdminFenceLogResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.AdminGuideSessionResponse;
import com.huohuo.scenicaiguide.tourist.dto.response.AdminLocationLogResponse;
import com.huohuo.scenicaiguide.tourist.entity.TouristFenceLog;
import com.huohuo.scenicaiguide.tourist.entity.TouristGuideSession;
import com.huohuo.scenicaiguide.tourist.entity.TouristLocationLog;
import com.huohuo.scenicaiguide.tourist.mapper.TouristFenceLogMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristGuideSessionMapper;
import com.huohuo.scenicaiguide.tourist.mapper.TouristLocationLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TouristAdminService {

    private final TouristGuideSessionMapper sessionMapper;
    private final TouristFenceLogMapper fenceLogMapper;
    private final TouristLocationLogMapper locationLogMapper;

    public TouristAdminService(TouristGuideSessionMapper sessionMapper,
                               TouristFenceLogMapper fenceLogMapper,
                               TouristLocationLogMapper locationLogMapper) {
        this.sessionMapper = sessionMapper;
        this.fenceLogMapper = fenceLogMapper;
        this.locationLogMapper = locationLogMapper;
    }

    public PageResponse<AdminGuideSessionResponse> listSessions(String keyword, String status, long page, long pageSize) {
        QueryWrapper<TouristGuideSession> wrapper = new QueryWrapper<TouristGuideSession>()
            .orderByDesc("updated_at");
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("session_no", keyword).or().like("visitor_subject", keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        Page<TouristGuideSession> result = sessionMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<AdminGuideSessionResponse> records = result.getRecords().stream()
            .map(AdminGuideSessionResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public PageResponse<AdminFenceLogResponse> listFenceLogs(Long sessionId, String keyword, String broadcastStatus,
                                                             long page, long pageSize) {
        QueryWrapper<TouristFenceLog> wrapper = new QueryWrapper<TouristFenceLog>()
            .orderByDesc("created_at");
        if (sessionId != null) {
            wrapper.eq("session_id", sessionId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("spot_name", keyword).or().like("spot_id", keyword));
        }
        if (broadcastStatus != null && !broadcastStatus.isBlank()) {
            wrapper.eq("broadcast_status", broadcastStatus.trim().toUpperCase());
        }
        Page<TouristFenceLog> result = fenceLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<AdminFenceLogResponse> records = result.getRecords().stream()
            .map(AdminFenceLogResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public PageResponse<AdminLocationLogResponse> listLocationLogs(Long sessionId, Integer accepted,
                                                                   long page, long pageSize) {
        QueryWrapper<TouristLocationLog> wrapper = new QueryWrapper<TouristLocationLog>()
            .orderByDesc("reported_at");
        if (sessionId != null) {
            wrapper.eq("session_id", sessionId);
        }
        if (accepted != null) {
            wrapper.eq("accepted", accepted);
        }
        Page<TouristLocationLog> result = locationLogMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<AdminLocationLogResponse> records = result.getRecords().stream()
            .map(AdminLocationLogResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }
}

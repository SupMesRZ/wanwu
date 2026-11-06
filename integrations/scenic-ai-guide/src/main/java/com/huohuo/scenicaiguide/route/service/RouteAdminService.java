package com.huohuo.scenicaiguide.route.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.route.dto.AdminAiRouteResponse;
import com.huohuo.scenicaiguide.route.entity.RouteAiRoute;
import com.huohuo.scenicaiguide.route.mapper.RouteAiRouteMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RouteAdminService {

    private final RouteAiRouteMapper aiRouteMapper;

    public RouteAdminService(RouteAiRouteMapper aiRouteMapper) {
        this.aiRouteMapper = aiRouteMapper;
    }

    public PageResponse<AdminAiRouteResponse> listAiRoutes(Long sessionId, String keyword, String status,
                                                           long page, long pageSize) {
        QueryWrapper<RouteAiRoute> wrapper = new QueryWrapper<RouteAiRoute>()
            .orderByDesc("created_at");
        if (sessionId != null) {
            wrapper.eq("session_id", sessionId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("name", keyword);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status.trim().toUpperCase());
        }
        Page<RouteAiRoute> result = aiRouteMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<AdminAiRouteResponse> records = result.getRecords().stream()
            .map(AdminAiRouteResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }
}

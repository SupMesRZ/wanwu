package com.huohuo.scenicaiguide.tourist.dto.response;

import lombok.Data;

/**
 * 路线跟随进度。游标推进到某个节点后随位置上报回传，
 * 前端据此高亮已完成站点并提示下一站
 */
@Data
public class RouteProgressResponse {

    private Long routeId;

    private String routeName;

    private Integer totalNodes;

    private Integer currentSequence;

    private String arrivedNodeName;

    private String nextNodeName;

    private boolean finished;
}

package com.huohuo.scenicaiguide.common.enums;

/**
 * 坐标系统枚举
 *
 * <p>WGS-84：GPS/北斗原始坐标，国际标准，数据库存储统一使用
 * <p>GCJ-02：国测局加密坐标（火星坐标），高德/腾讯/Google中国使用
 * <p>BD-09：百度加密坐标，百度地图使用
 */
public enum CoordinateSystem {

    /**
     * WGS-84 坐标系，GPS/北斗原始输出
     */
    WGS84,

    /**
     * GCJ-02 坐标系（火星坐标），高德/腾讯/Google中国
     */
    GCJ02,

    /**
     * BD-09 坐标系，百度地图
     */
    BD09
}

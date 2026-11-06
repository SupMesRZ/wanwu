package com.huohuo.scenicaiguide.common.util;

import com.huohuo.scenicaiguide.common.enums.CoordinateSystem;

/**
 * 坐标转换工具类
 *
 * <p>支持 WGS-84 / GCJ-02 / BD-09 三套坐标系互转
 * <p>算法基于国测局公开偏移公式和百度偏移公式实现
 * <p>转换精度约 1-2 米，满足景区导览场景（米级精度）需求
 */
public final class CoordinateConverter {

    private CoordinateConverter() {
    }

    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    /**
     * 通用转换入口
     *
     * @param lat  纬度
     * @param lng  经度
     * @param from 源坐标系
     * @param to   目标坐标系
     * @return double[]{lat, lng}
     */
    public static double[] convert(double lat, double lng, CoordinateSystem from, CoordinateSystem to) {
        if (from == to) {
            return new double[]{lat, lng};
        }
        return switch (from) {
            case WGS84 -> switch (to) {
                case GCJ02 -> wgs84ToGcj02(lat, lng);
                case BD09 -> wgs84ToBd09(lat, lng);
                default -> new double[]{lat, lng};
            };
            case GCJ02 -> switch (to) {
                case WGS84 -> gcj02ToWgs84(lat, lng);
                case BD09 -> gcj02ToBd09(lat, lng);
                default -> new double[]{lat, lng};
            };
            case BD09 -> switch (to) {
                case WGS84 -> bd09ToWgs84(lat, lng);
                case GCJ02 -> bd09ToGcj02(lat, lng);
                default -> new double[]{lat, lng};
            };
        };
    }

    // ========== WGS-84 ↔ GCJ-02 ==========

    /**
     * WGS-84 转 GCJ-02
     */
    public static double[] wgs84ToGcj02(double lat, double lng) {
        if (outOfChina(lat, lng)) {
            return new double[]{lat, lng};
        }
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dlng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dlng = (dlng * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{lat + dLat, lng + dlng};
    }

    /**
     * GCJ-02 转 WGS-84（迭代法近似）
     */
    public static double[] gcj02ToWgs84(double lat, double lng) {
        double[] wgs = {lat, lng};
        double[] tmp;
        double dLat;
        double dlng;
        for (int i = 0; i < 10; i++) {
            tmp = wgs84ToGcj02(wgs[0], wgs[1]);
            dLat = tmp[0] - lat;
            dlng = tmp[1] - lng;
            if (Math.abs(dLat) < 1e-6 && Math.abs(dlng) < 1e-6) {
                break;
            }
            wgs[0] -= dLat;
            wgs[1] -= dlng;
        }
        return wgs;
    }

    // ========== GCJ-02 ↔ BD-09 ==========

    /**
     * GCJ-02 转 BD-09
     */
    public static double[] gcj02ToBd09(double lat, double lng) {
        double z = Math.sqrt(lng * lng + lat * lat) + 0.00002 * Math.sin(lat * PI * 3000.0 / 180.0);
        double theta = Math.atan2(lat, lng) + 0.000003 * Math.cos(lng * PI * 3000.0 / 180.0);
        double bdLng = z * Math.cos(theta) + 0.0065;
        double bdLat = z * Math.sin(theta) + 0.006;
        return new double[]{bdLat, bdLng};
    }

    /**
     * BD-09 转 GCJ-02
     */
    public static double[] bd09ToGcj02(double lat, double lng) {
        double x = lng - 0.0065;
        double y = lat - 0.006;
        double z = Math.sqrt(x * x + y * y) - 0.00002 * Math.sin(y * PI * 3000.0 / 180.0);
        double theta = Math.atan2(y, x) - 0.000003 * Math.cos(x * PI * 3000.0 / 180.0);
        double gcjLng = z * Math.cos(theta);
        double gcjLat = z * Math.sin(theta);
        return new double[]{gcjLat, gcjLng};
    }

    // ========== WGS-84 ↔ BD-09（中转） ==========

    /**
     * WGS-84 转 BD-09
     */
    public static double[] wgs84ToBd09(double lat, double lng) {
        double[] gcj = wgs84ToGcj02(lat, lng);
        return gcj02ToBd09(gcj[0], gcj[1]);
    }

    /**
     * BD-09 转 WGS-84
     */
    public static double[] bd09ToWgs84(double lat, double lng) {
        double[] gcj = bd09ToGcj02(lat, lng);
        return gcj02ToWgs84(gcj[0], gcj[1]);
    }

    // ========== 私有工具方法 ==========

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 判断是否在中国境外（境外不偏移）
     */
    private static boolean outOfChina(double lat, double lng) {
        return lng < 72.004 || lng > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }
}

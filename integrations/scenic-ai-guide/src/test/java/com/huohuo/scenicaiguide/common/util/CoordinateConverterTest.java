package com.huohuo.scenicaiguide.common.util;

import com.huohuo.scenicaiguide.common.enums.CoordinateSystem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 坐标转换算法精度测试
 */
class CoordinateConverterTest {

    // 北京天安门坐标（已知参考值）
    // WGS-84: 39.9042, 116.4074
    // GCJ-02: 约 39.9055, 116.4113
    // BD-09: 约 39.9118, 116.4178
    private static final double BEIJING_LAT_WGS = 39.9042;
    private static final double BEIJING_LNG_WGS = 116.4074;

    // 允许误差：2 米 ≈ 0.00002 度
    private static final double DELTA = 0.00002;

    @Test
    void wgs84ToGcj02_andBack_shouldBeAccurate() {
        double[] gcj = CoordinateConverter.wgs84ToGcj02(BEIJING_LAT_WGS, BEIJING_LNG_WGS);
        double[] back = CoordinateConverter.gcj02ToWgs84(gcj[0], gcj[1]);

        assertClose(BEIJING_LAT_WGS, back[0], "纬度往返精度");
        assertClose(BEIJING_LNG_WGS, back[1], "经度往返精度");
    }

    @Test
    void wgs84ToBd09_andBack_shouldBeAccurate() {
        double[] bd = CoordinateConverter.wgs84ToBd09(BEIJING_LAT_WGS, BEIJING_LNG_WGS);
        double[] back = CoordinateConverter.bd09ToWgs84(bd[0], bd[1]);

        assertClose(BEIJING_LAT_WGS, back[0], "纬度往返精度");
        assertClose(BEIJING_LNG_WGS, back[1], "经度往返精度");
    }

    @Test
    void gcj02ToBd09_andBack_shouldBeAccurate() {
        double[] gcj = CoordinateConverter.wgs84ToGcj02(BEIJING_LAT_WGS, BEIJING_LNG_WGS);
        double[] bd = CoordinateConverter.gcj02ToBd09(gcj[0], gcj[1]);
        double[] back = CoordinateConverter.bd09ToGcj02(bd[0], bd[1]);

        assertClose(gcj[0], back[0], "纬度往返精度");
        assertClose(gcj[1], back[1], "经度往返精度");
    }

    @Test
    void convert_withSameSystem_shouldReturnOriginal() {
        double[] result = CoordinateConverter.convert(BEIJING_LAT_WGS, BEIJING_LNG_WGS,
            CoordinateSystem.WGS84, CoordinateSystem.WGS84);
        assertClose(BEIJING_LAT_WGS, result[0], "同系转换纬度");
        assertClose(BEIJING_LNG_WGS, result[1], "同系转换经度");
    }

    @Test
    void convert_allPaths_shouldWork() {
        // WGS84 -> GCJ02
        double[] gcj = CoordinateConverter.convert(BEIJING_LAT_WGS, BEIJING_LNG_WGS,
            CoordinateSystem.WGS84, CoordinateSystem.GCJ02);
        assertTrue(gcj[0] != BEIJING_LAT_WGS || gcj[1] != BEIJING_LNG_WGS, "WGS->GCJ 应该有偏移");

        // WGS84 -> BD09
        double[] bd = CoordinateConverter.convert(BEIJING_LAT_WGS, BEIJING_LNG_WGS,
            CoordinateSystem.WGS84, CoordinateSystem.BD09);
        assertTrue(bd[0] != BEIJING_LAT_WGS || bd[1] != BEIJING_LNG_WGS, "WGS->BD 应该有偏移");

        // BD09 -> GCJ02
        double[] gcjFromBd = CoordinateConverter.convert(bd[0], bd[1],
            CoordinateSystem.BD09, CoordinateSystem.GCJ02);
        assertClose(gcj[0], gcjFromBd[0], "BD->GCJ 纬度");
        assertClose(gcj[1], gcjFromBd[1], "BD->GCJ 经度");
    }

    @Test
    void outOfChina_shouldNotTransform() {
        // 纽约坐标，境外不应偏移
        double[] gcj = CoordinateConverter.wgs84ToGcj02(40.7128, -74.0060);
        assertClose(40.7128, gcj[0], "境外纬度不偏移");
        assertClose(-74.0060, gcj[1], "境外经度不偏移");
    }

    private void assertClose(double expected, double actual, String message) {
        double diff = Math.abs(expected - actual);
        assertTrue(diff < DELTA,
            message + " 误差过大: expected=" + expected + ", actual=" + actual + ", diff=" + diff);
    }
}

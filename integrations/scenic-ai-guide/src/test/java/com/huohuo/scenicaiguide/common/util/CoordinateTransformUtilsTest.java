package com.huohuo.scenicaiguide.common.util;

import com.huohuo.scenicaiguide.common.annotation.CoordinateField;
import com.huohuo.scenicaiguide.common.enums.CoordinateSystem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 坐标自动转换工具类测试
 */
class CoordinateTransformUtilsTest {

    // 北京天安门 WGS-84 坐标
    private static final double BEIJING_LAT = 39.9042;
    private static final double BEIJING_LNG = 116.4074;

    @Test
    void transform_doubleFields_shouldConvert() {
        Point point = new Point(BEIJING_LAT, BEIJING_LNG);
        CoordinateTransformUtils.transform(point, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        // GCJ-02 经度应大于 WGS-84（北京在东边，经度偏移为正）
        assertTrue(point.getLat() > BEIJING_LAT, "纬度应有偏移");
        assertTrue(point.getLng() > BEIJING_LNG, "经度应有偏移");
    }

    @Test
    void transform_floatFields_shouldConvert() {
        FloatPoint point = new FloatPoint((float) BEIJING_LAT, (float) BEIJING_LNG);
        CoordinateTransformUtils.transform(point, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        assertTrue(point.getLat() > BEIJING_LAT, "纬度应有偏移");
        assertTrue(point.getLng() > BEIJING_LNG, "经度应有偏移");
    }

    @Test
    void transform_stringFields_shouldConvert() {
        StringPoint point = new StringPoint(String.valueOf(BEIJING_LAT), String.valueOf(BEIJING_LNG));
        CoordinateTransformUtils.transform(point, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        double lat = Double.parseDouble(point.getLat());
        double lng = Double.parseDouble(point.getLng());
        assertTrue(lat > BEIJING_LAT, "纬度应有偏移");
        assertTrue(lng > BEIJING_LNG, "经度应有偏移");
    }

    @Test
    void transform_bigDecimalFields_shouldConvert() {
        BdPoint point = new BdPoint(BigDecimal.valueOf(BEIJING_LAT), BigDecimal.valueOf(BEIJING_LNG));
        CoordinateTransformUtils.transform(point, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        assertTrue(point.getLat().doubleValue() > BEIJING_LAT, "纬度应有偏移");
        assertTrue(point.getLng().doubleValue() > BEIJING_LNG, "经度应有偏移");
    }

    @Test
    void transform_nestedObject_shouldConvertRecursively() {
        Route route = new Route(new Point(BEIJING_LAT, BEIJING_LNG));
        CoordinateTransformUtils.transform(route, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        assertTrue(route.getStartPoint().getLat() > BEIJING_LAT, "嵌套纬度应有偏移");
        assertTrue(route.getStartPoint().getLng() > BEIJING_LNG, "嵌套经度应有偏移");
    }

    @Test
    void transform_collection_shouldConvertEachItem() {
        List<Point> points = List.of(
            new Point(BEIJING_LAT, BEIJING_LNG),
            new Point(31.2304, 121.4737) // 上海
        );
        CoordinateTransformUtils.transform(points, CoordinateSystem.WGS84, CoordinateSystem.GCJ02);

        assertTrue(points.get(0).getLat() != BEIJING_LAT, "列表第1项纬度应有偏移");
        assertTrue(points.get(1).getLat() != 31.2304, "列表第2项纬度应有偏移");
    }

    @Test
    void transform_sameSystem_shouldNotChange() {
        Point point = new Point(BEIJING_LAT, BEIJING_LNG);
        CoordinateTransformUtils.transform(point, CoordinateSystem.GCJ02, CoordinateSystem.GCJ02);

        assertEquals(BEIJING_LAT, point.getLat(), 1e-10, "同系转换纬度不变");
        assertEquals(BEIJING_LNG, point.getLng(), 1e-10, "同系转换经度不变");
    }

    @Test
    void transform_null_shouldNotThrow() {
        assertDoesNotThrow(() -> CoordinateTransformUtils.transform(null, CoordinateSystem.WGS84, CoordinateSystem.GCJ02));
    }

    @Test
    void transform_defaultSource_shouldUseWgs84() {
        Point point = new Point(BEIJING_LAT, BEIJING_LNG);
        // 省略 sourceSystem 参数，默认 WGS84
        CoordinateTransformUtils.transform(point, CoordinateSystem.GCJ02);

        assertTrue(point.getLat() > BEIJING_LAT, "默认从 WGS84 转换");
    }

    // ========== 测试实体 ==========

    static class Point {
        @CoordinateField
        private double lat;
        @CoordinateField
        private double lng;

        Point(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        double getLat() { return lat; }
        double getLng() { return lng; }
    }

    static class FloatPoint {
        @CoordinateField
        private float lat;
        @CoordinateField
        private float lng;

        FloatPoint(float lat, float lng) {
            this.lat = lat;
            this.lng = lng;
        }

        float getLat() { return lat; }
        float getLng() { return lng; }
    }

    static class StringPoint {
        @CoordinateField
        private String lat;
        @CoordinateField
        private String lng;

        StringPoint(String lat, String lng) {
            this.lat = lat;
            this.lng = lng;
        }

        String getLat() { return lat; }
        String getLng() { return lng; }
    }

    static class BdPoint {
        @CoordinateField
        private BigDecimal lat;
        @CoordinateField
        private BigDecimal lng;

        BdPoint(BigDecimal lat, BigDecimal lng) {
            this.lat = lat;
            this.lng = lng;
        }

        BigDecimal getLat() { return lat; }
        BigDecimal getLng() { return lng; }
    }

    static class Route {
        private Point startPoint;

        Route(Point startPoint) {
            this.startPoint = startPoint;
        }

        Point getStartPoint() { return startPoint; }
    }
}

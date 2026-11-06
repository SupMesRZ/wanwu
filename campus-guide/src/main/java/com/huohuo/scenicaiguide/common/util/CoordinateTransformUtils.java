package com.huohuo.scenicaiguide.common.util;

import com.huohuo.scenicaiguide.common.annotation.CoordinateField;
import com.huohuo.scenicaiguide.common.enums.CoordinateSystem;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * 坐标自动转换工具类
 *
 * <p>遍历对象树，自动识别并转换标注了 @CoordinateField 的经纬度字段
 * <p>字段名约定：lat/latitude 表示纬度，lng/lon/longitude 表示经度
 */
public final class CoordinateTransformUtils {

    private CoordinateTransformUtils() {
    }

    /**
     * 转换对象中的坐标字段（默认从 WGS-84 转换）
     *
     * @param obj        目标对象
     * @param targetSystem 目标坐标系
     */
    public static void transform(Object obj, CoordinateSystem targetSystem) {
        transform(obj, CoordinateSystem.WGS84, targetSystem);
    }

    /**
     * 转换对象中的坐标字段
     *
     * @param obj          目标对象
     * @param sourceSystem 源坐标系
     * @param targetSystem 目标坐标系
     */
    public static void transform(Object obj, CoordinateSystem sourceSystem, CoordinateSystem targetSystem) {
        if (obj == null || sourceSystem == targetSystem) {
            return;
        }
        if (obj instanceof Collection<?> collection) {
            for (Object item : collection) {
                transform(item, sourceSystem, targetSystem);
            }
        } else if (obj instanceof Map<?, ?> map) {
            for (Object value : map.values()) {
                transform(value, sourceSystem, targetSystem);
            }
        } else {
            transformObject(obj, sourceSystem, targetSystem);
        }
    }

    private static void transformObject(Object obj, CoordinateSystem sourceSystem, CoordinateSystem targetSystem) {
        if (obj == null) {
            return;
        }
        Class<?> clazz = obj.getClass();

        // 跳过 JDK 内置类型
        if (clazz.getName().startsWith("java.") || clazz.getName().startsWith("javax.") || clazz.isPrimitive()) {
            return;
        }

        Field latField = null;
        Field lngField = null;
        Double lat = null;
        Double lng = null;

        // 第一步：找当前对象的坐标字段
        for (Field field : clazz.getDeclaredFields()) {
            if (!field.isAnnotationPresent(CoordinateField.class)) {
                continue;
            }
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                double d = extractDouble(value);
                String name = field.getName().toLowerCase();
                if (name.contains("lat")) {
                    lat = d;
                    latField = field;
                } else if (name.contains("lng") || name.contains("lon")) {
                    lng = d;
                    lngField = field;
                }
            } catch (IllegalAccessException ignored) {
            }
        }

        // 转换坐标（lat/lng 非空时，latField/lngField 必然也非空）
        if (lat != null && lng != null) {
            double[] result = CoordinateConverter.convert(lat, lng, sourceSystem, targetSystem);
            try {
                setFieldValue(latField, obj, result[0]);
                setFieldValue(lngField, obj, result[1]);
            } catch (IllegalAccessException ignored) {
            }
        }

        // 第二步：递归处理嵌套对象和集合
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(obj);
                if (value == null) {
                    continue;
                }
                Class<?> fieldType = field.getType();
                if (fieldType.isPrimitive()
                    || fieldType == String.class
                    || fieldType == Boolean.class
                    || Number.class.isAssignableFrom(fieldType)
                    || fieldType.isEnum()) {
                    continue;
                }
                transform(value, sourceSystem, targetSystem);
            } catch (IllegalAccessException ignored) {
            }
        }
    }

    private static double extractDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static void setFieldValue(Field field, Object obj, double value) throws IllegalAccessException {
        Class<?> type = field.getType();
        if (type == double.class || type == Double.class) {
            field.set(obj, value);
        } else if (type == float.class || type == Float.class) {
            field.set(obj, (float) value);
        } else if (type == int.class || type == Integer.class) {
            field.set(obj, (int) Math.round(value));
        } else if (type == long.class || type == Long.class) {
            field.set(obj, Math.round(value));
        } else if (type == BigDecimal.class) {
            field.set(obj, BigDecimal.valueOf(value));
        } else if (type == String.class) {
            field.set(obj, String.valueOf(value));
        }
    }
}

package com.huohuo.scenicaiguide.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 坐标字段标记注解
 *
 * <p>标注在 DTO/Entity 的经纬度字段上，供 CoordinateTransformUtils 自动识别并转换
 * <p>字段名约定：lat/latitude 表示纬度，lng/lon/longitude 表示经度
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CoordinateField {
}

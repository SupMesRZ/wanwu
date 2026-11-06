package com.huohuo.scenicaiguide.common.api;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.util.List;

/**
 * 分页响应对象
 *
 * @param records    当前页数据列表
 * @param page       当前页码（从 1 开始）
 * @param pageSize   每页大小
 * @param total      总记录数
 * @param totalPages 总页数
 * @param <T>        数据类型
 */
public record PageResponse<T>(
        List<T> records,
        long page,
        long pageSize,
        long total,
        long totalPages
) {

    /**
     * 构造分页响应
     *
     * @param records  当前页数据
     * @param page     当前页码
     * @param pageSize 每页大小
     * @param total    总记录数
     * @param <T>      数据类型
     * @return 分页响应对象
     */
    public static <T> PageResponse<T> of(List<T> records, long page, long pageSize, long total) {
        long pages = pageSize > 0 ? (total + pageSize - 1) / pageSize : 0;
        return new PageResponse<>(records, page, pageSize, total, pages);
    }

    /**
     * 从 MyBatis-Plus Page 构造分页响应
     *
     * @param page MyBatis-Plus 分页对象
     * @param <T>  数据类型
     * @return 分页响应对象
     */
    public static <T> PageResponse<T> from(Page<T> page) {
        return of(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }
}

package com.easyoa.common.response;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 统一分页响应结构。列表接口必须分页（见产品规范「性能」章节）。
 *
 * @param items      当前页数据
 * @param page       页码（从 1 开始）
 * @param size       每页数量
 * @param total      总记录数
 * @param totalPages 总页数
 */
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        int totalPages = size <= 0 ? 0 : (int) ((total + size - 1) / size);
        return new PageResponse<>(items, page, size, total, totalPages);
    }

    /** 由 Spring Data 的查询结果构建（页码对外从 1 开始）。 */
    public static <T> PageResponse<T> from(Page<T> result) {
        return of(result.getContent(), result.getNumber() + 1, result.getSize(), result.getTotalElements());
    }

    /** 由 Spring Data 的查询结果构建，并映射元素类型。 */
    public static <S, T> PageResponse<T> from(Page<S> result, Function<S, T> mapper) {
        return of(result.getContent().stream().map(mapper).toList(), result.getNumber() + 1, result.getSize(),
                result.getTotalElements());
    }
}
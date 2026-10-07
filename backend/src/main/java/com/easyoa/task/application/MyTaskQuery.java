package com.easyoa.task.application;

/**
 * 「我的任务」查询条件。
 */
public record MyTaskQuery(MyTaskFilter filter, String keyword, int page, int size) {

    public enum MyTaskFilter {
        /** 未结束（待处理 / 进行中 / 待审核）。 */
        OPEN,
        /** 即将到期（未结束且截止时间在阈值内，含已逾期）。 */
        DUE_SOON,
        /** 已结束（DONE / CLOSED）。 */
        DONE,
        /** 全部。 */
        ALL
    }

    public static MyTaskQuery of(String filter, String keyword, Integer page, Integer size) {
        MyTaskFilter parsed = MyTaskFilter.OPEN;
        if (filter != null && !filter.isBlank()) {
            try {
                parsed = MyTaskFilter.valueOf(filter.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                parsed = MyTaskFilter.OPEN;
            }
        }
        String normalized = keyword == null || keyword.isBlank() ? null : "%" + keyword.trim().toLowerCase() + "%";
        return new MyTaskQuery(parsed, normalized, page == null ? 1 : page,
                size == null ? 15 : Math.min(size, 100));
    }
}
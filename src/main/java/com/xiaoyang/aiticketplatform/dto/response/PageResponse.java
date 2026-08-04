package com.xiaoyang.aiticketplatform.dto.response;

import java.util.List;
import java.util.Objects;

public record PageResponse<T>(
        List<T> records,
        long total,
        long pages,
        long current,
        long size
) {
    public PageResponse {
        Objects.requireNonNull(records, "records 不能为空");
        if (total < 0) {
            throw new IllegalArgumentException("total 不能小于0");
        }
        if (pages < 0) {
            throw new IllegalArgumentException("pages 不能小于0");
        }
        if (current < 1) {
            throw new IllegalArgumentException("current 必须大于等于1");
        }
        if (size < 1) {
            throw new IllegalArgumentException("size 必须大于等于1");
        }
        records = List.copyOf(records);
    }
}

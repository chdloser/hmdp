package com.hmdp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ScrollResult {
    /**
     * 通用数据List
     */
    private List<?> list;
    /**
     * 上一次时间
     */
    private Long minTime;
    /**
     * 偏移量
     */
    private Integer offset;
}

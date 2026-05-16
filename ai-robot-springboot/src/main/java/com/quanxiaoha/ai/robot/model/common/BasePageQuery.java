package com.quanxiaoha.ai.robot.model.common;

import lombok.Data;

/** 分页查询公共参数 */
@Data
public class BasePageQuery {

    /** 当前页码，默认 1 */
    private Long current = 1L;

    /** 每页条数，默认 10 */
    private Long size = 10L;
}

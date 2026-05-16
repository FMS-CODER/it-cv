package com.quanxiaoha.ai.robot.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 联网搜索结果条目 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultDTO {

    /** 页面链接 */
    private String url;

    /** 相关性得分 */
    private Double score;

    /** 页面正文摘要 */
    private String content;
}

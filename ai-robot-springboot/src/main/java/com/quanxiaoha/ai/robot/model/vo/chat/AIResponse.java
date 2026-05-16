package com.quanxiaoha.ai.robot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** AI 流式响应片段（SSE） */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AIResponse {

    /** 正文内容 */
    private String v;

    /** 推理过程（深度思考模型） */
    private String reasoning;
}

package com.quanxiaoha.ai.robot.model.vo.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 重命名会话请求 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class RenameChatReqVO {

    /** 会话主键 ID */
    @NotNull(message = "对话 ID 不能为空")
    private Long id;

    /** 新摘要/标题 */
    @NotBlank(message = "对话摘要不能为空")
    private String summary;
}

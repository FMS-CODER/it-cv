package com.quanxiaoha.ai.robot.model.vo.chat;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 删除会话请求 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteChatReqVO {

    /** 会话 UUID */
    @NotBlank(message = "对话 UUID 不能为空")
    private String uuid;
}

package com.quanxiaoha.ai.robot.model.vo.chat;

import com.quanxiaoha.ai.robot.model.common.BasePageQuery;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 会话消息分页请求 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindChatHistoryMessagePageListReqVO extends BasePageQuery {

    /** 会话 UUID */
    @NotBlank(message = "对话 ID 不能为空")
    private String chatId;
}

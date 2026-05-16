package com.quanxiaoha.ai.robot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 新建会话响应 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NewChatRspVO {

    /** 会话摘要/标题 */
    private String summary;

    /** 会话 UUID */
    private String uuid;
}

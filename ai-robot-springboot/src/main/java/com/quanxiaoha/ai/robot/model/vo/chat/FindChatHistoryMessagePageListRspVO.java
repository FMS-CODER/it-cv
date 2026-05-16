package com.quanxiaoha.ai.robot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 会话消息分页项 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindChatHistoryMessagePageListRspVO {

    /** 消息主键 */
    private Long id;

    /** 所属会话 UUID */
    private String chatId;

    /** 消息正文 */
    private String content;

    /** 角色：user / assistant */
    private String role;

    /** 发送时间 */
    private LocalDateTime createTime;
}

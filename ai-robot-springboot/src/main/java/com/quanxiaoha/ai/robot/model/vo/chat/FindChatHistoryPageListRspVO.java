package com.quanxiaoha.ai.robot.model.vo.chat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 会话列表分页项 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindChatHistoryPageListRspVO {

    /** 主键 ID */
    private Long id;

    /** 会话 UUID */
    private String uuid;

    /** 摘要/标题 */
    private String summary;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}

package com.quanxiaoha.ai.robot.domain.dos;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** 对话消息表 t_chat_message */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("t_chat_message")
public class ChatMessageDO {

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属会话 UUID */
    private String chatUuid;

    /** 消息正文 */
    private String content;

    /** 推理过程内容 */
    private String reasoningContent;

    /** 角色：user / assistant */
    private String role;

    /** 创建时间 */
    private LocalDateTime createTime;
}

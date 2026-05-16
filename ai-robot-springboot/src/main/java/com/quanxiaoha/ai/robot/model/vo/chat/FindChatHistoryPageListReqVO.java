package com.quanxiaoha.ai.robot.model.vo.chat;

import com.quanxiaoha.ai.robot.model.common.BasePageQuery;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/** 会话列表分页请求 */
@Data
@AllArgsConstructor
@Builder
public class FindChatHistoryPageListReqVO extends BasePageQuery {
}

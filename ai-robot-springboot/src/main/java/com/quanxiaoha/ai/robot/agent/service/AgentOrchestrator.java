package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.ResumeOptimizeAgentRequest;
import com.quanxiaoha.ai.robot.model.vo.chat.AIResponse;
import com.quanxiaoha.ai.robot.model.vo.chat.AiChatReqVO;
import reactor.core.publisher.Flux;

/**
 * 统一 Agent 编排入口。
 */
public interface AgentOrchestrator {

    Flux<AIResponse> streamChat(AiChatReqVO reqVO);

    Flux<String> streamResumeOptimize(ResumeOptimizeAgentRequest request);
}

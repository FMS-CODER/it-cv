package com.quanxiaoha.ai.robot.agent.service.impl;

import com.google.common.collect.Lists;
import com.quanxiaoha.ai.robot.advisor.CustomChatMemoryAdvisor;
import com.quanxiaoha.ai.robot.advisor.CustomStreamLoggerAndMessage2DBAdvisor;
import com.quanxiaoha.ai.robot.advisor.NetworkSearchAdvisor;
import com.quanxiaoha.ai.robot.agent.model.AgentContext;
import com.quanxiaoha.ai.robot.agent.model.AgentScene;
import com.quanxiaoha.ai.robot.agent.model.PlannerDecision;
import com.quanxiaoha.ai.robot.agent.model.ReActStep;
import com.quanxiaoha.ai.robot.agent.model.ResumeOptimizeAgentRequest;
import com.quanxiaoha.ai.robot.agent.service.AgentAuditLogger;
import com.quanxiaoha.ai.robot.agent.service.AgentOrchestrator;
import com.quanxiaoha.ai.robot.agent.service.AgentPlannerService;
import com.quanxiaoha.ai.robot.agent.service.SearchToolFacade;
import com.quanxiaoha.ai.robot.agent.step.ContextAssemblerStep;
import com.quanxiaoha.ai.robot.agent.step.IntentClassifierStep;
import com.quanxiaoha.ai.robot.agent.step.PromptBuilderStep;
import com.quanxiaoha.ai.robot.agent.tool.SearchAgentTools;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMapper;
import com.quanxiaoha.ai.robot.domain.mapper.ChatMessageMapper;
import com.quanxiaoha.ai.robot.model.vo.chat.AIResponse;
import com.quanxiaoha.ai.robot.model.vo.chat.AiChatReqVO;
import com.quanxiaoha.ai.robot.service.SearXNGService;
import com.quanxiaoha.ai.robot.service.SearchResultContentFetcherService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SignalType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 统一 Agent 编排器。
 */
@Service
@Slf4j
public class AgentOrchestratorImpl implements AgentOrchestrator {

    @Resource
    private IntentClassifierStep intentClassifierStep;
    @Resource
    private ContextAssemblerStep contextAssemblerStep;
    @Resource
    private PromptBuilderStep promptBuilderStep;
    @Resource
    private AgentAuditLogger agentAuditLogger;
    @Resource
    private AgentPlannerService agentPlannerService;
    @Resource
    private SearchToolFacade searchToolFacade;
    @Resource
    private SearchAgentTools searchAgentTools;

    @Resource(name = "deepSeekChatModel")
    private ChatModel chatModel;
    @Resource
    private ChatMapper chatMapper;
    @Resource
    private ChatMessageMapper chatMessageMapper;
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private SearXNGService searXNGService;
    @Resource
    private SearchResultContentFetcherService searchResultContentFetcherService;

    @Override
    public Flux<AIResponse> streamChat(AiChatReqVO reqVO) {
        boolean forceKnowledgeRag = Boolean.TRUE.equals(reqVO.getKnowledgeRag());
        boolean forceNetworkSearch = Boolean.TRUE.equals(reqVO.getNetworkSearch());
        boolean forceSearchTools = Boolean.TRUE.equals(reqVO.getSearchToolEnabled());
        boolean forcePlanner = Boolean.TRUE.equals(reqVO.getAgentPlanner());
        AgentContext context = AgentContext.builder()
                .requestId(StringUtils.defaultIfBlank(reqVO.getTraceId(), null))
                .scene(AgentScene.CHAT)
                .chatId(reqVO.getChatId())
                .userInput(reqVO.getMessage())
                .modelName(reqVO.getModelName())
                .temperature(reqVO.getTemperature())
                .networkSearch(forceNetworkSearch)
                .knowledgeRag(forceKnowledgeRag)
                .kbCategory(reqVO.getKbCategory())
                .kbTopK(reqVO.getKbTopK() == null ? 5 : reqVO.getKbTopK())
                .searchToolEnabled(true)
                .agentPlannerEnabled(true)
                .maxAgentSteps(reqVO.getMaxAgentSteps() == null ? 3 : Math.max(1, reqVO.getMaxAgentSteps()))
                .build();

        agentAuditLogger.recordRunStart(context);
        runFoundationSteps(context);
        if (context.isAgentPlannerEnabled()) {
            runPlanner(context, forceKnowledgeRag, forceNetworkSearch, forceSearchTools, forcePlanner);
        }
        agentAuditLogger.recordPrompt(context);

        Flux<AIResponse> stream;
        if (context.isSearchToolEnabled()) {
            stream = reactLoop(context, reqVO)
                    .onErrorResume(ex -> {
                        agentAuditLogger.recordFallback(context, ex);
                        return streamChatWithFallbackLoop(context, reqVO);
                    });
        } else {
            stream = streamChatStandard(context, reqVO, true);
        }
        return wrapChatStream(context, stream);
    }

    @Override
    public Flux<String> streamResumeOptimize(ResumeOptimizeAgentRequest request) {
        boolean forceKnowledgeRag = request.isKnowledgeRag();
        boolean forceSearchTool = request.isSearchToolEnabled();
        boolean forcePlanner = request.isAgentPlanner();
        AgentContext context = AgentContext.builder()
                .requestId(StringUtils.defaultIfBlank(request.getTraceId(), null))
                .scene(AgentScene.RESUME_OPTIMIZE)
                .resumeText(request.getResumeText())
                .targetPosition(request.getTargetPosition())
                .additionalRequirements(request.getAdditionalRequirements())
                .knowledgeRag(forceKnowledgeRag)
                .kbCategory(request.getKbCategory())
                .kbTopK(request.getKbTopK())
                .searchToolEnabled(true)
                .agentPlannerEnabled(true)
                .maxAgentSteps(request.getMaxAgentSteps())
                .build();

        agentAuditLogger.recordRunStart(context);
        runFoundationSteps(context);
        if (context.isAgentPlannerEnabled()) {
            runPlanner(context, forceKnowledgeRag, false, forceSearchTool, forcePlanner);
        }
        agentAuditLogger.recordPrompt(context);
        agentAuditLogger.recordToolMode(context, "resume-direct");
        Flux<String> stream = ChatClient.create(chatModel)
                .prompt()
                .system(context.getSystemPrompt())
                .user(context.getUserPrompt())
                .stream()
                .content()
                .map(this::toJsonEvent);
        return wrapTextStream(context, stream);
    }

    private void runFoundationSteps(AgentContext context) {
        long start = System.currentTimeMillis();
        runStep(context, intentClassifierStep, () -> "intent=" + context.getIntent());
        runStep(context, contextAssemblerStep, () -> "ragLength=" + StringUtils.length(context.getRagContext()));
        runStep(context, promptBuilderStep, () -> "promptReady");
        context.setFoundationDurationMs(System.currentTimeMillis() - start);
    }

    private void runPlanner(AgentContext context,
                            boolean forceKnowledgeRag,
                            boolean forceNetworkSearch,
                            boolean forceSearchTools,
                            boolean forcePlanner) {
        long start = System.currentTimeMillis();
        PlannerDecision decision = agentPlannerService.planChat(context);
        context.setPlannerDecision(decision);
        if (decision.getIntent() != null) {
            context.setIntent(decision.getIntent());
        }
        context.setAgentPlannerEnabled(forcePlanner || context.isAgentPlannerEnabled());
        context.setKnowledgeRag(forceKnowledgeRag || decision.isNeedKnowledgeSearch());
        context.setNetworkSearch(forceNetworkSearch || decision.isNeedWebSearch());
        context.setSearchToolEnabled(forceSearchTools || decision.isUseSearchTools());
        agentAuditLogger.recordPlannerDecision(context, decision);
        agentAuditLogger.recordStep(context, "PlannerStep",
                System.currentTimeMillis() - start,
                "intent=" + decision.getIntent() + ", toolQuery=" + decision.getToolQuery());
        // Planner 决策可能改变意图或检索开关，因此重新装配上下文和 prompt
        runStep(context, contextAssemblerStep, () -> "plannerAdjustedRagLength=" + StringUtils.length(context.getRagContext()));
        runStep(context, promptBuilderStep, () -> "plannerAdjustedPromptReady");
        context.setPlannerDurationMs(System.currentTimeMillis() - start);
    }

    private Flux<AIResponse> streamChatWithNativeTools(AgentContext context, AiChatReqVO reqVO) {
        agentAuditLogger.recordToolMode(context, "native");
        ChatClient.ChatClientRequestSpec spec = ChatClient.create(chatModel)
                .prompt()
                .system(context.getSystemPrompt())
                .user(context.getUserPrompt())
                .tools(searchAgentTools);
        spec.advisors(buildChatAdvisors(reqVO, false, true));
        return mapChatStream(spec);
    }

    /**
     * ReAct 多工具调用循环：每轮模型可同时调用多个工具（knowledge_search + web_search），
     * 根据结构化轨迹自主决定是否继续。
     */
    private Flux<AIResponse> reactLoop(AgentContext context, AiChatReqVO reqVO) {
        PlannerDecision decision = context.getPlannerDecision();
        if (decision == null) {
            decision = agentPlannerService.planChat(context);
            context.setPlannerDecision(decision);
            agentAuditLogger.recordPlannerDecision(context, decision);
        }

        int maxStepsRaw = Math.min(decision.getMaxReactSteps(), context.getMaxAgentSteps());
        final int maxSteps = maxStepsRaw < 1 ? 1 : maxStepsRaw;
        final PlannerDecision finalDecision = decision;

        if (!finalDecision.isInitialToolCall()) {
            return streamChatStandard(context, reqVO, true);
        }

        context.setReactFinished(false);
        context.setCurrentReactStep(0);

        return Flux.create(sink -> {
            try {
                String currentSystemPrompt = context.getSystemPrompt();
                String userInput = context.getUserInput();
                StringBuilder reactTrace = new StringBuilder();

                for (int step = 0; step < maxSteps; step++) {
                    context.setCurrentReactStep(step);
                    long stepStart = System.currentTimeMillis();

                    String reactPrompt = buildReactPrompt(currentSystemPrompt, userInput, reactTrace.toString(), finalDecision);
                    String modelResponse = ChatClient.create(chatModel)
                            .prompt()
                            .system(reactPrompt)
                            .user(userInput)
                            .tools(searchAgentTools)
                            .call()
                            .content();

                    if (StringUtils.isBlank(modelResponse)) {
                        sink.complete();
                        return;
                    }

                    ReActStep.ReActStepBuilder stepBuilder = ReActStep.builder()
                            .stepIndex(step)
                            .durationMs(System.currentTimeMillis() - stepStart);

                    String thought = extractThought(modelResponse);
                    stepBuilder.thought(thought);

                    List<ReActStep.ReActToolCall> toolCalls = extractToolCalls(modelResponse);
                    stepBuilder.toolCalls(toolCalls);

                    if (!toolCalls.isEmpty()) {
                        for (ReActStep.ReActToolCall tc : toolCalls) {
                            String result = executeToolCall(context, tc.getToolName(), tc.getToolArgs());
                            tc.setToolResult(result);
                        }

                        StringBuilder observation = new StringBuilder();
                        for (ReActStep.ReActToolCall tc : toolCalls) {
                            observation.append("【").append(tc.getToolName()).append(" 结果】\n")
                                    .append(tc.getToolResult()).append("\n\n");
                        }
                        stepBuilder.observation(observation.toString().trim());

                        reactTrace.append("【第").append(step + 1).append("轮】\n");
                        reactTrace.append("思考：").append(thought).append("\n");
                        for (ReActStep.ReActToolCall tc : toolCalls) {
                            reactTrace.append("调用工具：").append(tc.getToolName())
                                    .append("(").append(tc.getToolArgs()).append(")\n");
                        }
                        reactTrace.append("观察结果：").append(observation).append("\n\n");
                    } else {
                        context.setReactFinished(true);
                        context.setReactFinalAnswer(modelResponse);
                        ReActStep finalStep = stepBuilder.observation("").build();
                        context.getReactTraces().add(finalStep);

                        sink.next(AIResponse.builder().v(modelResponse).build());
                        sink.complete();
                        return;
                    }

                    context.getReactTraces().add(stepBuilder.build());
                }

                context.setReactFinished(true);
                String finalAnswer = ChatClient.create(chatModel)
                        .prompt()
                        .system(buildReactPrompt(currentSystemPrompt, userInput, reactTrace.toString(), finalDecision)
                                + "\n\n请基于以上所有工具结果给出最终完整回答。")
                        .user(userInput)
                        .call()
                        .content();
                context.setReactFinalAnswer(finalAnswer);
                sink.next(AIResponse.builder().v(StringUtils.defaultString(finalAnswer)).build());
                sink.complete();

            } catch (Exception e) {
                log.error("ReAct 循环异常: {}", e.getMessage(), e);
                sink.error(e);
            }
        });
    }

    private String buildReactPrompt(String baseSystemPrompt, String userInput, String reactTrace,
                                    PlannerDecision decision) {
        StringBuilder sb = new StringBuilder();
        sb.append(baseSystemPrompt).append("\n\n");

        sb.append("## ReAct 工具调用协议\n");
        sb.append("你有两个工具可用：\n");
        sb.append("- knowledge_search(query)：查询内部知识库\n");
        sb.append("- web_search(query)：联网搜索最新信息\n\n");
        sb.append("每轮你可以同时调用多个工具，也可以不调用工具直接回答。\n\n");

        sb.append("请按以下格式输出：\n");
        sb.append("思考：<你的推理过程>\n");
        sb.append("工具调用：[\n");
        sb.append("  {\"name\": \"knowledge_search\", \"args\": \"查询词\"},\n");
        sb.append("  {\"name\": \"web_search\", \"args\": \"查询词\"}\n");
        sb.append("]\n");
        sb.append("—— 或者 ——\n");
        sb.append("思考：<你的推理过程>\n");
        sb.append("最终回答：<你的最终答案>\n\n");

        if (StringUtils.isNotBlank(reactTrace)) {
            sb.append("## 历史工具调用轨迹\n");
            sb.append(reactTrace).append("\n");
            sb.append("请根据以上轨迹判断：如果信息已足够，直接输出最终回答；如果信息不足，继续调用工具。\n\n");
        }

        sb.append("工具策略：").append(decision.getToolStrategy()).append("\n");
        sb.append("查询词：").append(StringUtils.defaultIfBlank(decision.getToolQuery(), userInput)).append("\n");

        return sb.toString();
    }

    private String extractThought(String modelResponse) {
        if (StringUtils.isBlank(modelResponse)) return "";
        int thoughtStart = modelResponse.indexOf("思考：");
        if (thoughtStart < 0) return modelResponse.length() > 100 ? modelResponse.substring(0, 100) : modelResponse;
        int toolStart = modelResponse.indexOf("工具调用：");
        int answerStart = modelResponse.indexOf("最终回答：");
        int end = modelResponse.length();
        if (toolStart > thoughtStart) end = Math.min(end, toolStart);
        if (answerStart > thoughtStart) end = Math.min(end, answerStart);
        return modelResponse.substring(thoughtStart + 3, end).trim();
    }

    private List<ReActStep.ReActToolCall> extractToolCalls(String modelResponse) {
        List<ReActStep.ReActToolCall> calls = new ArrayList<>();
        if (StringUtils.isBlank(modelResponse)) return calls;

        int toolSectionStart = modelResponse.indexOf("工具调用：");
        if (toolSectionStart < 0) return calls;

        String toolSection = modelResponse.substring(toolSectionStart + 5).trim();
        int answerStart = toolSection.indexOf("最终回答：");
        if (answerStart > 0) {
            toolSection = toolSection.substring(0, answerStart).trim();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode arr = mapper.readTree(toolSection);
            if (arr.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : arr) {
                    String name = node.path("name").asText("");
                    String args = node.path("args").asText("");
                    if (StringUtils.isNotBlank(name)) {
                        calls.add(ReActStep.ReActToolCall.builder()
                                .toolName(name)
                                .toolArgs(args)
                                .build());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析工具调用 JSON 失败: {}", e.getMessage());
        }

        return calls;
    }

    private String executeToolCall(AgentContext context, String toolName, String toolArgs) {
        if (StringUtils.isBlank(toolName)) return "";
        String query = StringUtils.defaultIfBlank(toolArgs, context.getUserInput());

        switch (toolName) {
            case "knowledge_search":
                return searchToolFacade.searchKnowledgeWithRerank(query, context.getKbCategory(), context.getKbTopK());
            case "web_search":
                return searchToolFacade.searchWeb(query, Math.min(context.getKbTopK(), 5));
            default:
                return "未知工具: " + toolName;
        }
    }

    private Flux<AIResponse> streamChatWithFallbackLoop(AgentContext context, AiChatReqVO reqVO) {
        agentAuditLogger.recordToolMode(context, "fallback");
        PlannerDecision decision = context.getPlannerDecision();
        if (decision == null) {
            decision = agentPlannerService.planChat(context);
            context.setPlannerDecision(decision);
            agentAuditLogger.recordPlannerDecision(context, decision);
        }

        if (context.getMaxAgentSteps() < 2) {
            log.warn("Agent 最大步数不足以执行工具降级流程，直接使用普通生成：maxSteps={}", context.getMaxAgentSteps());
            return streamChatStandard(context, reqVO, true);
        }

        boolean needKnowledge = decision.isNeedKnowledgeSearch();
        boolean needWeb = decision.isNeedWebSearch();
        if (!needKnowledge && !needWeb) {
            return streamChatStandard(context, reqVO, true);
        }

        String toolQuery = StringUtils.defaultIfBlank(decision.getToolQuery(), context.getUserInput());
        StringBuilder system = new StringBuilder(StringUtils.defaultString(context.getSystemPrompt()));
        system.append("\n\n## 工具搜索结果\n");
        if (needKnowledge) {
            long knowledgeStart = System.currentTimeMillis();
            system.append(searchToolFacade.searchKnowledge(toolQuery, context.getKbCategory(), context.getKbTopK()))
                    .append("\n\n");
            context.setKnowledgeSearchDurationMs(System.currentTimeMillis() - knowledgeStart);
        }
        if (needWeb) {
            long webStart = System.currentTimeMillis();
            system.append(searchToolFacade.searchWeb(toolQuery, Math.min(context.getKbTopK(), 5)))
                    .append("\n\n");
            context.setWebSearchDurationMs(System.currentTimeMillis() - webStart);
        }
        system.append("请基于以上工具结果继续回答，优先引用检索结果中的关键信息。");
        context.setSystemPrompt(system.toString().trim());
        context.setFinalPromptPreview("[System]\n" + context.getSystemPrompt() + "\n\n[User]\n" + context.getUserPrompt());
        agentAuditLogger.recordPrompt(context);
        return streamChatStandard(context, reqVO, false);
    }

    private Flux<AIResponse> streamChatStandard(AgentContext context, AiChatReqVO reqVO, boolean allowNetworkAdvisor) {
        if (StringUtils.isBlank(context.getToolMode())) {
            agentAuditLogger.recordToolMode(context, "standard");
        }
        ChatClient.ChatClientRequestSpec spec = ChatClient.create(chatModel)
                .prompt()
                .system(context.getSystemPrompt())
                .user(context.getUserPrompt());
        spec.advisors(buildChatAdvisors(reqVO, allowNetworkAdvisor, false));
        return mapChatStream(spec);
    }

    private List<Advisor> buildChatAdvisors(AiChatReqVO reqVO, boolean allowNetworkAdvisor, boolean alwaysUseMemory) {
        List<Advisor> advisors = Lists.newArrayList();
        if (allowNetworkAdvisor && Boolean.TRUE.equals(reqVO.getNetworkSearch())) {
            advisors.add(new NetworkSearchAdvisor(searXNGService, searchResultContentFetcherService));
        } else if (alwaysUseMemory || StringUtils.isNotBlank(reqVO.getChatId())) {
            advisors.add(new CustomChatMemoryAdvisor(chatMessageMapper, reqVO, 50));
        }

        if (StringUtils.isNotBlank(reqVO.getChatId())) {
            advisors.add(new CustomStreamLoggerAndMessage2DBAdvisor(chatMapper, chatMessageMapper, reqVO, transactionTemplate));
        }
        return advisors;
    }

    private Flux<AIResponse> mapChatStream(ChatClient.ChatClientRequestSpec spec) {
        return spec.stream()
                .chatResponse()
                .mapNotNull(chatResponse -> {
                    if (Objects.nonNull(chatResponse) && Objects.nonNull(chatResponse.getResult())) {
                        AssistantMessage message = chatResponse.getResult().getOutput();
                        String text = message.getText();
                        Object reasoningObj = message.getMetadata() != null
                                ? message.getMetadata().get("reasoningContent") : null;
                        String reasoningContent = reasoningObj != null ? reasoningObj.toString() : null;
                        if (StringUtils.isNotBlank(reasoningContent)) {
                            return AIResponse.builder().reasoning(reasoningContent).build();
                        }
                        return AIResponse.builder().v(text).build();
                    }
                    return null;
                });
    }

    private Flux<AIResponse> wrapChatStream(AgentContext context, Flux<AIResponse> source) {
        AtomicBoolean finished = new AtomicBoolean(false);
        return source.doOnNext(item -> {
                    if (context.getFirstTokenLatencyMs() <= 0) {
                        agentAuditLogger.recordFirstToken(context);
                    }
                    context.setOutputChunks(context.getOutputChunks() + 1);
                    if (item != null) {
                        context.setOutputChars(context.getOutputChars() + StringUtils.length(item.getV()));
                        context.setReasoningChars(context.getReasoningChars() + StringUtils.length(item.getReasoning()));
                    }
                })
                .doOnComplete(() -> finishRun(context, true, null, finished))
                .doOnError(ex -> finishRun(context, false, ex == null ? null : ex.getMessage(), finished))
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        finishRun(context, false, "stream cancelled", finished);
                    }
                });
    }

    private Flux<String> wrapTextStream(AgentContext context, Flux<String> source) {
        AtomicBoolean finished = new AtomicBoolean(false);
        return source.doOnNext(item -> {
                    if (context.getFirstTokenLatencyMs() <= 0) {
                        agentAuditLogger.recordFirstToken(context);
                    }
                    context.setOutputChunks(context.getOutputChunks() + 1);
                    context.setOutputChars(context.getOutputChars() + StringUtils.length(item));
                })
                .doOnComplete(() -> finishRun(context, true, null, finished))
                .doOnError(ex -> finishRun(context, false, ex == null ? null : ex.getMessage(), finished))
                .doFinally(signalType -> {
                    if (signalType == SignalType.CANCEL) {
                        finishRun(context, false, "stream cancelled", finished);
                    }
                });
    }

    private void finishRun(AgentContext context, boolean success, String errorMessage, AtomicBoolean finished) {
        if (finished.compareAndSet(false, true)) {
            agentAuditLogger.recordRunFinished(context, success, errorMessage);
        }
    }

    private void runStep(AgentContext context,
                         com.quanxiaoha.ai.robot.agent.step.AgentStep step,
                         java.util.function.Supplier<String> summarySupplier) {
        long start = System.currentTimeMillis();
        step.execute(context);
        agentAuditLogger.recordStep(context, step.getName(),
                System.currentTimeMillis() - start,
                summarySupplier.get());
    }

    private String toJsonEvent(String text) {
        String safe = text == null ? "" : text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
        return "{\"v\": \"" + safe + "\"}";
    }
}

package com.quanxiaoha.ai.robot.controller;

import com.quanxiaoha.ai.robot.aspect.ApiOperationLog;
import com.quanxiaoha.ai.robot.metrics.AgentBenchmarkService;
import com.quanxiaoha.ai.robot.metrics.AgentMetricReport;
import com.quanxiaoha.ai.robot.metrics.AgentRunMetrics;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Agent 指标验证接口。
 */
@RestController
@RequestMapping("/agent-metrics")
@CrossOrigin(origins = "http://localhost:5174", allowCredentials = "true")
public class AgentMetricsController {

    @Resource
    private AgentBenchmarkService agentBenchmarkService;

    @PostMapping("/baseline")
    @ApiOperationLog(description = "查询 Agent 能力基线")
    public Response<Map<String, Object>> baseline(@RequestBody(required = false) Map<String, Object> payload) {
        int recentLimit = 10;
        if (payload != null && payload.get("recentLimit") != null) {
            try {
                recentLimit = Integer.parseInt(String.valueOf(payload.get("recentLimit")));
            } catch (NumberFormatException ignored) {
            }
        }
        return Response.success(agentBenchmarkService.buildBaseline(recentLimit));
    }

    @PostMapping("/runs/recent")
    @ApiOperationLog(description = "查询最近 Agent 运行指标")
    public Response<List<AgentRunMetrics>> recentRuns(@RequestBody(required = false) Map<String, Object> payload) {
        int limit = 20;
        if (payload != null && payload.get("limit") != null) {
            try {
                limit = Integer.parseInt(String.valueOf(payload.get("limit")));
            } catch (NumberFormatException ignored) {
            }
        }
        return Response.success(agentBenchmarkService.recentRuns(limit));
    }

    @PostMapping("/benchmark/rag")
    @ApiOperationLog(description = "执行 RAG 指标评测")
    public Response<Map<String, Object>> ragBenchmark(@RequestBody(required = false) Map<String, Object> payload) {
        return Response.success(agentBenchmarkService.runRagBenchmark(payload == null ? Map.of() : payload));
    }

    @PostMapping("/benchmark/web")
    @ApiOperationLog(description = "执行联网搜索指标评测")
    public Response<Map<String, Object>> webBenchmark(@RequestBody(required = false) Map<String, Object> payload) {
        return Response.success(agentBenchmarkService.runWebBenchmark(payload == null ? Map.of() : payload));
    }

    @PostMapping("/benchmark/chat")
    @ApiOperationLog(description = "执行对话与 SSE 指标评测")
    public Response<Map<String, Object>> chatBenchmark(@RequestBody(required = false) Map<String, Object> payload) {
        return Response.success(agentBenchmarkService.runChatBenchmark(payload == null ? Map.of() : payload));
    }

    @PostMapping("/report")
    @ApiOperationLog(description = "生成指标报告与简历输出")
    public Response<AgentMetricReport> report() {
        return Response.success(agentBenchmarkService.generateReport());
    }
}

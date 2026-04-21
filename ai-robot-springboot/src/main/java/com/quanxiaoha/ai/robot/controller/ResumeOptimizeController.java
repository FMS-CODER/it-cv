package com.quanxiaoha.ai.robot.controller;

import com.quanxiaoha.ai.robot.agent.model.ResumeOptimizeAgentRequest;
import com.quanxiaoha.ai.robot.agent.service.AgentOrchestrator;
import jakarta.annotation.Resource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * @Author: 小明
 * @Date: 2025/5/22 12:25
 * @Version: v1.0.0
 * @Description: 简历优化控制器
 **/
@RestController
@RequestMapping("/resume-optimize")
@CrossOrigin(origins = "http://localhost:5174", allowCredentials = "true")
public class ResumeOptimizeController {

    @Resource
    private AgentOrchestrator agentOrchestrator;

    /**
     * 上传简历文件
     * @param file 简历文件（PDF 或 DOCX）
     * @return 解析后的简历文本
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> uploadResume(@RequestParam("file") MultipartFile file) {
        try {
            // 验证文件大小（50MB）
            long maxSize = 50 * 1024 * 1024; // 50MB
            if (file.getSize() > maxSize) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "文件大小不能超过 50MB"
                ));
            }

            // 验证文件类型
            String fileName = file.getOriginalFilename();
            if (fileName == null || (!fileName.endsWith(".pdf") && !fileName.endsWith(".docx"))) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "error", "不支持的文件格式，请上传 PDF 或 DOCX 文件"
                ));
            }

            String text;
            if (fileName.endsWith(".pdf")) {
                // 使用 PDF 解析
                text = extractTextFromPdf(file);
            } else {
                // 使用 POI 解析 DOCX
                text = extractTextFromDocx(file);
            }
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "text", text
            ));
            
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "文件读取失败：" + e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "处理失败：" + e.getMessage()
            ));
        }
    }

    /**
     * 从 PDF 文件中提取文本（PDF 为二进制，不能用 UTF-8 整包解码，否则乱码）
     */
    private String extractTextFromPdf(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            return text != null ? text.trim() : "";
        }
    }

    /**
     * 从 DOCX 文件中提取文本（DOCX 为 ZIP+XML，需用 POI 解析，不能当纯文本 UTF-8 读）
     */
    private String extractTextFromDocx(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             XWPFDocument document = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph p : document.getParagraphs()) {
                String t = p.getText();
                if (t != null && !t.isBlank()) {
                    sb.append(t).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String c = cell.getText();
                        if (c != null) {
                            sb.append(c).append('\t');
                        }
                    }
                    sb.append('\n');
                }
            }
            return sb.toString().trim();
        }
    }

    /**
     * 优化简历（流式输出）
     * @param resumeText 简历文本
     * @param targetPosition 目标岗位
     * @param additionalRequirements 额外要求
     * @return 流式输出的优化建议
     */
    @PostMapping(value = "/optimize", produces = "text/event-stream;charset=utf-8")
    public Flux<String> optimizeResume(@RequestBody Map<String, Object> params) {
        ResumeOptimizeAgentRequest request = ResumeOptimizeAgentRequest.builder()
                .traceId(stringParam(params.get("traceId")))
                .resumeText(stringParam(params.get("resumeText")))
                .targetPosition(stringParam(params.get("targetPosition")))
                .additionalRequirements(stringParam(params.get("additionalRequirements")))
                .knowledgeRag(boolParam(params.get("knowledgeRag")))
                .kbCategory(stringParam(params.get("kbCategory")))
                .kbTopK(intParam(params.get("kbTopK"), 5))
                .searchToolEnabled(boolParam(params.get("searchToolEnabled")))
                .agentPlanner(boolParam(params.get("agentPlanner")))
                .maxAgentSteps(intParam(params.get("maxAgentSteps"), 3))
                .build();
        return agentOrchestrator.streamResumeOptimize(request);
    }

    private static String stringParam(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static boolean boolParam(Object v) {
        if (v == null) {
            return false;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        return "true".equalsIgnoreCase(String.valueOf(v).trim());
    }

    private static int intParam(Object v, int defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

}

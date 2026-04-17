package com.quanxiaoha.ai.robot.controller;

import com.quanxiaoha.ai.robot.aspect.ApiOperationLog;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @Author: 小明
 * @Date: 2026/4/17
 * @Version: v1.0.0
 * @Description: 简历知识库
 */
@RestController
@RequestMapping("/resume-kb")
@CrossOrigin(origins = "http://localhost:5174", allowCredentials = "true")
public class ResumeKnowledgeBaseController {

    @Resource
    private ResumeKnowledgeBaseService resumeKnowledgeBaseService;

    @PostMapping("/import")
    @ApiOperationLog(description = "导入简历知识库（批量）")
    public Response<ImportResumeKnowledgeRspVO> importBatch(@RequestBody @Validated ImportResumeKnowledgeReqVO reqVO) {
        return resumeKnowledgeBaseService.importBatch(reqVO);
    }

    @PostMapping("/import/samples")
    @ApiOperationLog(description = "导入简历知识库（典型示例）")
    public Response<ImportResumeKnowledgeRspVO> importSamples() {
        return resumeKnowledgeBaseService.importSamples();
    }

    @PostMapping("/page/list")
    @ApiOperationLog(description = "分页查询简历知识库")
    public PageResponse<FindResumeKnowledgePageListRspVO> pageList(@RequestBody @Validated FindResumeKnowledgePageListReqVO reqVO) {
        return resumeKnowledgeBaseService.pageList(reqVO);
    }
}


package com.quanxiaoha.ai.robot.controller;

import com.quanxiaoha.ai.robot.aspect.ApiOperationLog;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.FindResumeKnowledgePageListRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.ImportResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.SearchResumeKnowledgeRspVO;
import com.quanxiaoha.ai.robot.model.vo.knowledge.UpdateResumeKnowledgeReqVO;
import com.quanxiaoha.ai.robot.service.ResumeKnowledgeBaseService;
import com.quanxiaoha.ai.robot.utils.PageResponse;
import com.quanxiaoha.ai.robot.utils.Response;
import jakarta.annotation.Resource;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    /**
     * 更新单条知识并同步重算 embedding（与前端「编辑保存」一致）
     */
    @PostMapping("/update")
    @ApiOperationLog(description = "更新简历知识库单条并重新向量化")
    public Response<Boolean> updateKnowledge(@RequestBody @Validated UpdateResumeKnowledgeReqVO reqVO) {
        return resumeKnowledgeBaseService.updateKnowledge(reqVO);
    }

    @PostMapping("/page/list")
    @ApiOperationLog(description = "分页查询简历知识库")
    public PageResponse<FindResumeKnowledgePageListRspVO> pageList(@RequestBody @Validated FindResumeKnowledgePageListReqVO reqVO) {
        return resumeKnowledgeBaseService.pageList(reqVO);
    }

    /**
     * 回填历史数据的向量字段（适用于：早期未配置 EmbeddingModel 时导入的数据）
     */
    @PostMapping("/embedding/refill")
    @ApiOperationLog(description = "回填简历知识库向量字段")
    public Response<Integer> refillEmbeddings(@RequestParam(value = "batchSize", required = false) Integer batchSize) {
        return resumeKnowledgeBaseService.refillEmbeddings(batchSize);
    }

    /**
     * 基于 DashScope 向量 + pgvector 余弦距离的相似度检索
     */
    @PostMapping("/search")
    @ApiOperationLog(description = "知识库向量相似度检索")
    public Response<List<SearchResumeKnowledgeRspVO>> search(@RequestBody @Validated SearchResumeKnowledgeReqVO reqVO) {
        return resumeKnowledgeBaseService.searchSimilar(reqVO);
    }
}

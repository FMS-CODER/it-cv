package com.quanxiaoha.ai.robot.agent.service;

import com.quanxiaoha.ai.robot.agent.model.RankedDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 相似度截断策略单元测试：覆盖硬下限、软阈值、顶部截断与保底逻辑 */
class RagTruncationServiceTest {

    private RagTruncationService service;

    @BeforeEach
    void setUp() {
        service = new RagTruncationService();
        ReflectionTestUtils.setField(service, "similarityThreshold", 0.35);
        ReflectionTestUtils.setField(service, "maxTopK", 8);
        ReflectionTestUtils.setField(service, "hardFloor", 0.2);
        ReflectionTestUtils.setField(service, "minKeep", 1);
    }

    private RankedDocument doc(long id, double score) {
        return RankedDocument.builder().id(id).content("片段" + id).vectorScore(score).build();
    }

    /** 达标片段正常保留，低于软阈值的弱相关片段不注入 */
    @Test
    void keepsOnlyQualifiedDocuments() {
        List<RankedDocument> input = List.of(doc(1, 0.9), doc(2, 0.8), doc(3, 0.5), doc(4, 0.3));
        List<RankedDocument> result = service.truncateBySimilarity(input);
        assertEquals(3, result.size());
        assertEquals(1L, result.get(0).getId());
        assertEquals(3L, result.get(2).getId());
    }

    /** 达标片段超过上限时按分数截断 */
    @Test
    void capsAtMaxTopK() {
        List<RankedDocument> input = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            input.add(doc(i, 0.9));
        }
        List<RankedDocument> result = service.truncateBySimilarity(input);
        assertEquals(8, result.size());
    }

    /** 全部低于软阈值但高于硬下限：按 min-keep 保底，避免上下文静默为空 */
    @Test
    void fallsBackToMinKeepWhenAllBelowSoftThreshold() {
        List<RankedDocument> input = List.of(doc(1, 0.30), doc(2, 0.25), doc(3, 0.22));
        List<RankedDocument> result = service.truncateBySimilarity(input);
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
    }

    /** 全部低于硬下限：判定为完全无关，返回空 */
    @Test
    void dropsEverythingBelowHardFloor() {
        List<RankedDocument> input = List.of(doc(1, 0.19), doc(2, 0.10));
        assertTrue(service.truncateBySimilarity(input).isEmpty());
    }

    /** 空输入安全返回 */
    @Test
    void emptyInputReturnsEmpty() {
        assertTrue(service.truncateBySimilarity(List.of()).isEmpty());
    }
}

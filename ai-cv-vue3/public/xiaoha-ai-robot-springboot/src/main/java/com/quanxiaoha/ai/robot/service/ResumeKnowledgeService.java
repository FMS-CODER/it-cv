package com.quanxiaoha.ai.robot.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class ResumeKnowledgeService {

    private final List<String> sampleKnowledge = new ArrayList<>();

    public ResumeKnowledgeService() {
        initSampleData();
    }

    public List<String> searchSimilar(String query, int topK) {
        log.info("搜索相似内容 - query: {}, topK: {}", query, topK);
        
        List<String> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        
        for (String knowledge : sampleKnowledge) {
            if (knowledge.toLowerCase().contains(lowerQuery) || results.size() < topK) {
                results.add(knowledge);
            }
            if (results.size() >= topK) {
                break;
            }
        }
        
        return results;
    }

    public List<String> searchSimilarWithCategory(String query, String category, int topK) {
        return searchSimilar(query, topK);
    }

    public void initSampleData() {
        log.info("初始化简历优化知识库示例数据");
        
        sampleKnowledge.add("简历写作技巧：\n" +
                "1. 使用动词开头：负责、主导、实现、优化、设计、重构\n" +
                "2. 量化成果：提升了X%，节省了Y时间，处理了Z数据量\n" +
                "3. 突出技术难点：遇到的问题、解决方案、最终结果\n" +
                "4. 针对性定制：针对不同岗位调整简历内容\n" +
                "5. 简洁有力：一页纸原则，突出重点，避免冗余");
        
        sampleKnowledge.add("Java 面试知识点：\n" +
                "基础部分：\n" +
                "- 集合框架：HashMap、ArrayList、ConcurrentHashMap 原理\n" +
                "- 多线程：线程池、synchronized、Lock、CAS、volatile\n" +
                "- JVM：内存结构、GC 算法、类加载机制、性能调优\n" +
                "- IO/NIO：BIO、NIO、Netty\n" +
                "\n" +
                "框架部分：\n" +
                "- Spring：IOC、AOP 原理\n" +
                "- Spring Boot：自动配置、starter 原理\n" +
                "- Spring MVC：工作流程、拦截器\n" +
                "- MyBatis：#{} 与 ${} 区别、一级二级缓存");
        
        sampleKnowledge.add("面试自我介绍模板（1-2分钟）：\n" +
                "面试官您好，我是张三，有2年Java后端开发经验。\n" +
                "之前在某某公司负责电商平台核心模块开发，主要使用 Spring Boot + Spring Cloud 技术栈。\n" +
                "我参与过订单系统重构、性能优化等重点项目，有高并发场景的实践经验。\n" +
                "我熟悉 MySQL、Redis、消息队列等中间件，对微服务架构有一定理解。\n" +
                "希望能加入贵公司，贡献我的技术能力。");
        
        sampleKnowledge.add("职业发展建议 - 初级工程师（0-2年）：\n" +
                "1. 夯实基础：数据结构、算法、操作系统、计算机网络\n" +
                "2. 多写代码：每周完成1-2个小项目\n" +
                "3. 阅读源码：理解框架原理\n" +
                "4. 写技术博客：记录学习过程\n" +
                "5. 找 mentor：寻求前辈指导\n" +
                "\n" +
                "职业发展建议 - 中级工程师（2-5年）：\n" +
                "1. 技术深度：深入研究某一领域\n" +
                "2. 架构能力：学习系统设计\n" +
                "3. 项目管理：推动项目落地\n" +
                "4. 团队协作：带领新人成长\n" +
                "5. 技术视野：关注行业趋势");
        
        log.info("示例数据初始化完成，共 {} 条", sampleKnowledge.size());
    }
}

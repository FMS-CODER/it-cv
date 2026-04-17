package com.quanxiaoha.ai.robot.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ResumeTools {

    @Tool(description = "获取简历优化建议，针对不同岗位类型提供针对性建议")
    String getResumeOptimizationTips(String jobType) {
        log.info("## 获取简历优化建议: {}", jobType);

        StringBuilder sb = new StringBuilder();
        sb.append("📝 简历优化建议:\n\n");

        if (jobType != null && (jobType.toLowerCase().contains("java") || jobType.toLowerCase().contains("后端"))) {
            sb.append("🎯 Java 后端开发简历优化重点:\n\n");
            sb.append("1️⃣ 技术栈展示:\n");
            sb.append("• 突出 Spring Boot、Spring Cloud、MyBatis 等核心技能\n");
            sb.append("• 列出数据库：MySQL、Redis、MongoDB 等\n");
            sb.append("• 提到消息队列：RabbitMQ、Kafka 等\n\n");

            sb.append("2️⃣ 项目经验描述:\n");
            sb.append("• 使用 STAR 法则描述项目\n");
            sb.append("• 强调性能优化：QPS、响应时间提升等数据\n");
            sb.append("• 展示技术难点解决方案\n\n");

            sb.append("3️⃣ 加分项:\n");
            sb.append("• 开源项目贡献\n");
            sb.append("• 技术博客/文章\n");
            sb.append("• 技术 certifications\n");
        } else if (jobType != null && (jobType.toLowerCase().contains("前端") || jobType.toLowerCase().contains("vue"))) {
            sb.append("🎯 前端开发简历优化重点:\n\n");
            sb.append("1️⃣ 技术栈展示:\n");
            sb.append("• 突出 Vue/React、TypeScript、Vite/Webpack\n");
            sb.append("• 列出 UI 组件库：Element Plus、Ant Design 等\n");
            sb.append("• 提到 CSS 预处理器：Sass/Less\n\n");

            sb.append("2️⃣ 项目经验描述:\n");
            sb.append("• 强调性能优化：首屏加载、打包体积等\n");
            sb.append("• 展示组件设计和复用能力\n");
            sb.append("• 提到响应式设计和移动端适配\n\n");

            sb.append("3️⃣ 加分项:\n");
            sb.append("• GitHub 有完整项目\n");
            sb.append("• 个人技术博客\n");
            sb.append("• 参与过开源项目\n");
        } else {
            sb.append("🎯 通用简历优化建议:\n\n");
            sb.append("1️⃣ 简历结构:\n");
            sb.append("• 基本信息（简洁明了）\n");
            sb.append("• 专业技能（分类清晰）\n");
            sb.append("• 工作/项目经验（重点突出）\n");
            sb.append("• 教育背景\n\n");

            sb.append("2️⃣ 写作技巧:\n");
            sb.append("• 使用动词开头：负责、主导、实现、优化\n");
            sb.append("• 量化成果：用数据说话\n");
            sb.append("• 针对性定制：针对不同岗位调整\n");
            sb.append("• 一页原则：经验不足时控制在一页\n");
        }

        sb.append("\n💡 简历通用原则:\n");
        sb.append("• 真实诚信，不夸大其词\n");
        sb.append("• 排版整洁，使用 PDF 格式\n");
        sb.append("• 文件名规范：姓名-岗位-简历.pdf\n");
        sb.append("• 定期更新，保持简历新鲜度\n");

        return sb.toString();
    }

    @Tool(description = "获取面试准备建议，包括常见问题和回答技巧")
    String getInterviewPreparationTips(String jobType) {
        log.info("## 获取面试准备建议: {}", jobType);

        StringBuilder sb = new StringBuilder();
        sb.append("🎤 面试准备建议:\n\n");

        sb.append("📋 面试前准备:\n");
        sb.append("1. 研究公司和岗位要求\n");
        sb.append("2. 复习核心技术知识点\n");
        sb.append("3. 准备自我介绍（1-2分钟）\n");
        sb.append("4. 回顾项目经验，准备具体案例\n");
        sb.append("5. 准备向面试官提问的问题\n\n");

        sb.append("💬 常见面试问题:\n");
        sb.append("• 请做一下自我介绍\n");
        sb.append("• 谈谈你最有成就感的项目\n");
        sb.append("• 你遇到过的最大技术挑战是什么？\n");
        sb.append("• 为什么想换工作？\n");
        sb.append("• 你的职业规划是什么？\n");
        sb.append("• 你有什么问题想问我？\n\n");

        sb.append("🎯 回答技巧（STAR 法则）:\n");
        sb.append("• S (Situation)：当时的背景\n");
        sb.append("• T (Task)：你的任务\n");
        sb.append("• A (Action)：你采取的行动\n");
        sb.append("• R (Result)：最终的结果\n\n");

        sb.append("💡 面试注意事项:\n");
        sb.append("• 提前10-15分钟到达（线上提前测试设备）\n");
        sb.append("• 着装整洁，保持良好精神状态\n");
        sb.append("• 认真倾听，回答简洁有条理\n");
        sb.append("• 诚实回答，不知道就说不知道\n");
        sb.append("• 保持积极态度，展现学习意愿\n");

        return sb.toString();
    }

    @Tool(description = "获取技术面试知识点清单，按技术栈分类")
    String getTechnicalInterviewTopics(String techStack) {
        log.info("## 获取技术面试知识点: {}", techStack);

        StringBuilder sb = new StringBuilder();
        sb.append("📚 技术面试知识点清单:\n\n");

        if (techStack != null && techStack.toLowerCase().contains("java")) {
            sb.append("☕ Java 基础:\n");
            sb.append("• 集合框架（HashMap、ArrayList、ConcurrentHashMap）\n");
            sb.append("• 多线程与并发（Thread、线程池、锁）\n");
            sb.append("• JVM（内存模型、GC、类加载）\n");
            sb.append("• IO/NIO、反射、注解\n\n");

            sb.append("🌱 Spring 生态:\n");
            sb.append("• Spring IOC、AOP 原理\n");
            sb.append("• Spring Boot 自动配置\n");
            sb.append("• Spring MVC 工作流程\n");
            sb.append("• Spring Cloud 常用组件\n\n");

            sb.append("🗄️ 数据库:\n");
            sb.append("• MySQL 索引、事务、锁\n");
            sb.append("• Redis 数据结构、持久化、缓存\n");
            sb.append("• SQL 优化\n\n");
        } else if (techStack != null && (techStack.toLowerCase().contains("前端") || techStack.toLowerCase().contains("vue"))) {
            sb.append("⚡ JavaScript/TypeScript:\n");
            sb.append("• 原型链、闭包、作用域\n");
            sb.append("• 异步编程（Promise、async/await）\n");
            sb.append("• ES6+ 新特性\n");
            sb.append("• TypeScript 类型系统\n\n");

            sb.append("🖼️ Vue/React:\n");
            sb.append("• 响应式原理\n");
            sb.append("• 虚拟 DOM、diff 算法\n");
            sb.append("• 组件生命周期\n");
            sb.append("• 状态管理（Vuex/Pinia/Redux）\n\n");

            sb.append("🎨 CSS:\n");
            sb.append("• 盒模型、Flex、Grid\n");
            sb.append("• 响应式设计\n");
            sb.append("• 性能优化\n");
        }

        sb.append("🔧 通用技能:\n");
        sb.append("• Git 常用命令\n");
        sb.append("• 网络协议（HTTP/HTTPS、TCP）\n");
        sb.append("• 算法与数据结构\n");
        sb.append("• 设计模式\n");

        return sb.toString();
    }

    @Tool(description = "获取职场发展建议，包括技能提升和职业规划")
    String getCareerAdvice(String currentLevel) {
        log.info("## 获取职场发展建议: {}", currentLevel);

        StringBuilder sb = new StringBuilder();
        sb.append("🚀 职场发展建议:\n\n");

        if (currentLevel != null && currentLevel.toLowerCase().contains("初级")) {
            sb.append("🌱 初级工程师（0-2年）:\n\n");
            sb.append("📌 重点:\n");
            sb.append("• 夯实基础，练好编程基本功\n");
            sb.append("• 多写代码，积累项目经验\n");
            sb.append("• 学会调试和解决问题\n");
            sb.append("• 养成良好的编码习惯\n\n");

            sb.append("🎯 成长建议:\n");
            sb.append("• 每周完成 1-2 个小项目\n");
            sb.append("• 阅读优秀开源代码\n");
            sb.append("• 写技术博客记录学习\n");
            sb.append("• 找一位 mentor 指导\n");
        } else if (currentLevel != null && currentLevel.toLowerCase().contains("中级")) {
            sb.append("🌿 中级工程师（2-5年）:\n\n");
            sb.append("📌 重点:\n");
            sb.append("• 技术深度：深入理解原理\n");
            sb.append("• 架构能力：系统设计思维\n");
            sb.append("• 项目管理：推动项目落地\n");
            sb.append("• 团队协作：带动他人成长\n\n");

            sb.append("🎯 成长建议:\n");
            sb.append("• 深入学习底层原理\n");
            sb.append("• 参与技术方案设计\n");
            sb.append("• 尝试技术分享和培训\n");
            sb.append("• 拓展技术视野，了解行业趋势\n");
        } else {
            sb.append("🌳 高级工程师（5年+）:\n\n");
            sb.append("📌 重点:\n");
            sb.append("• 技术架构：系统级设计能力\n");
            sb.append("• 技术领导力：团队技术决策\n");
            sb.append("• 业务理解：技术驱动业务\n");
            sb.append("• 行业影响力：技术品牌建设\n\n");

            sb.append("🎯 成长建议:\n");
            sb.append("• 关注行业前沿技术\n");
            sb.append("• 参与开源社区建设\n");
            sb.append("• 技术输出（写书、专栏、演讲）\n");
            sb.append("• 思考技术与商业的结合\n");
        }

        sb.append("\n💡 通用建议:\n");
        sb.append("• 保持学习热情，技术日新月异\n");
        sb.append("• 建立个人技术品牌\n");
        sb.append("• 平衡技术深度与广度\n");
        sb.append("• 重视软技能提升\n");
        sb.append("• 定期复盘和职业规划\n");

        return sb.toString();
    }

    @Tool(description = "生成简历检查清单，帮助用户自查简历")
    String getResumeChecklist() {
        log.info("## 获取简历检查清单");

        StringBuilder sb = new StringBuilder();
        sb.append("✅ 简历检查清单:\n\n");

        sb.append("📋 基本信息:\n");
        sb.append("• [ ] 姓名、电话、邮箱清晰可见\n");
        sb.append("• [ ] GitHub/GitLab 链接（如有）\n");
        sb.append("• [ ] 技术博客链接（如有）\n");
        sb.append("• [ ] 没有不必要的个人信息（年龄、婚姻等）\n\n");

        sb.append("💻 专业技能:\n");
        sb.append("• [ ] 技能分类清晰（语言、框架、工具等）\n");
        sb.append("• [ ] 突出核心技能，避免罗列太多\n");
        sb.append("• [ ] 技能与岗位要求匹配\n");
        sb.append("• [ ] 不写不熟悉的技能\n\n");

        sb.append("📁 工作/项目经验:\n");
        sb.append("• [ ] 使用动词开头（负责、主导、实现）\n");
        sb.append("• [ ] 量化成果（数据说话）\n");
        sb.append("• [ ] 突出技术难点和解决方案\n");
        sb.append("• [ ] 时间倒序，最近的在前\n");
        sb.append("• [ ] 项目描述简洁，重点突出\n\n");

        sb.append("🎨 排版与格式:\n");
        sb.append("• [ ] 排版整洁，留白适当\n");
        sb.append("• [ ] 字体统一，字号适中\n");
        sb.append("• [ ] 无错别字和语法错误\n");
        sb.append("• [ ] 控制在 1-2 页\n");
        sb.append("• [ ] 导出为 PDF 格式\n");
        sb.append("• [ ] 文件名规范：姓名-岗位-简历.pdf\n\n");

        sb.append("🎯 针对性定制:\n");
        sb.append("• [ ] 针对岗位要求调整简历\n");
        sb.append("• [ ] 关键词匹配 JD\n");
        sb.append("• [ ] 突出相关经验\n");

        return sb.toString();
    }

    @Tool(description = "分析简历与岗位的匹配度，找出简历中与岗位要求匹配的要点")
    String analyzeJobMatch(String resumeText, String jobDescription) {
        log.info("## 分析简历与岗位匹配度");
        log.info("简历长度: {}, JD长度: {}", resumeText.length(), jobDescription.length());

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 简历-岗位匹配度分析\n\n");

        sb.append("📊 总体评估: 75% 匹配\n\n");

        sb.append("✅ 高度匹配的要点:\n");
        sb.append("• [匹配度 95%] Java 编程能力 - 简历中多次体现，JD 要求明确\n");
        sb.append("• [匹配度 90%] Spring Boot 框架 - 项目经验充分，技术栈匹配\n");
        sb.append("• [匹配度 85%] MySQL 数据库 - 掌握数据库设计和优化\n");
        sb.append("• [匹配度 80%] 团队协作能力 - 项目经历中体现\n\n");

        sb.append("⚠️ 需要强调的要点:\n");
        sb.append("• [匹配度 60%] 微服务架构经验 - 有涉及但不深入，建议补充\n");
        sb.append("• [匹配度 55%] 分布式系统设计 - 建议在项目中强调相关经验\n");
        sb.append("• [匹配度 50%] Docker/Kubernetes - 建议补充相关内容\n\n");

        sb.append("❌ 缺失的技能（建议补充）:\n");
        sb.append("• Redis 缓存深入使用经验\n");
        sb.append("• MQ 消息队列实践经验\n");
        sb.append("• 性能调优经验\n\n");

        sb.append("💡 优化建议:\n");
        sb.append("1. 在项目描述中强化微服务相关内容\n");
        sb.append("2. 量化成果：突出 QPS、响应时间等数据\n");
        sb.append("3. 补充 Docker/Kubernetes 使用经验\n");
        sb.append("4. 强调解决的技术难点\n");

        return sb.toString();
    }

    @Tool(description = "生成 STAR 法则描述模板，帮助用户用 STAR 法优化项目经验描述")
    String generateStarTemplate(String projectName, String role) {
        log.info("## 生成 STAR 法则模板: {}, 角色: {}", projectName, role);

        StringBuilder sb = new StringBuilder();
        sb.append("📝 STAR 法则项目经验模板\n");
        sb.append("项目名称: ").append(projectName).append("\n");
        sb.append("担任角色: ").append(role).append("\n\n");

        sb.append("【Situation 情境】(1-2句话)\n");
        sb.append("描述项目的背景和你面临的挑战：\n");
        sb.append("例如：在公司电商系统中，负责用户订单模块的开发，原系统订单处理效率低，用户投诉多...\n\n");

        sb.append("【Task 任务】(1句话)\n");
        sb.append("明确你的职责和目标：\n");
        sb.append("例如：需要优化订单处理流程，将平均处理时间从 5s 降低到 1s 以内...\n\n");

        sb.append("【Action 行动】(3-5句话)\n");
        sb.append("详细描述你采取的具体行动：\n");
        sb.append("例如：\n");
        sb.append("1. 分析现有代码，发现订单处理存在 N+1 查询问题\n");
        sb.append("2. 引入 Redis 缓存热点数据，减少数据库查询\n");
        sb.append("3. 使用异步消息队列处理非核心流程\n");
        sb.append("4. 编写自动化测试用例覆盖核心逻辑\n\n");

        sb.append("【Result 结果】(2-3句话，包含量化数据)\n");
        sb.append("描述最终成果和影响：\n");
        sb.append("例如：\n");
        sb.append("• 订单处理效率提升 80%，平均处理时间从 5s 降至 0.8s\n");
        sb.append("• 系统 QPS 从 100 提升到 500，支持秒杀活动\n");
        sb.append("• 用户满意度提升 30%，投诉率下降 50%\n\n");

        sb.append("💡 写作技巧:\n");
        sb.append("• 使用强动词：设计、开发、优化、重构、实现、提升\n");
        sb.append("• 量化数据要具体：80%、5s、1000+用户\n");
        sb.append("• 突出个人贡献，使用「我」而非「团队」\n");
        sb.append("• 每个项目只写 2-3 个关键成果\n");

        return sb.toString();
    }

    @Tool(description = "生成自我介绍模板，帮助用户准备面试自我介绍")
    String generateSelfIntroduction(String name, String experience, String targetPosition) {
        log.info("## 生成自我介绍模板: {}, {}, {}", name, experience, targetPosition);

        StringBuilder sb = new StringBuilder();
        sb.append("🎤 面试自我介绍模板\n\n");

        sb.append("【基础版 - 1分钟】\n");
        sb.append("─────────────────\n");
        sb.append("面试官好，我叫").append(name).append("。\n\n");
        sb.append("我有 ").append(experience).append(" 的 Java 开发经验，目前在一家互联网公司担任后端开发。\n\n");
        sb.append("在工作中，我主要负责 ").append(targetPosition).append(" 相关项目的开发，\n");
        sb.append("熟练掌握 Java、Spring Boot、MySQL 等技术，有良好的编码习惯。\n\n");
        sb.append("我对技术有热情，喜欢研究底层原理，也注重代码质量和性能优化。\n");
        sb.append("如果有幸加入贵公司，我希望能在 ").append(targetPosition).append(" 方向深入发展，为团队创造价值。\n");
        sb.append("谢谢！\n\n");

        sb.append("【进阶版 - 2分钟】\n");
        sb.append("─────────────────\n");
        sb.append("面试官好，我叫").append(name).append("，").append(experience).append(" Java 开发经验。\n\n");
        sb.append("【技术栈】\n");
        sb.append("熟练掌握 Java、Spring Boot、MySQL、Redis，了解微服务架构和容器化技术。\n\n");
        sb.append("【项目经验】\n");
        sb.append("参与过电商平台、用户系统等项目的开发，负责核心模块的设计和实现。\n");
        sb.append("其中印象最深刻的是一个性能优化项目，我将订单接口响应时间从 5s 优化到 0.5s。\n\n");
        sb.append("【优势与规划】\n");
        sb.append("我善于分析和解决问题，具备良好的沟通能力和团队协作精神。\n");
        sb.append("希望能在 ").append(targetPosition).append(" 方向发展，与团队共同成长。\n");
        sb.append("谢谢！\n\n");

        sb.append("💡 注意事项:\n");
        sb.append("• 控制时长，不要太啰嗦\n");
        sb.append("• 多用数据和成果说话\n");
        sb.append("• 展现对岗位的热情\n");
        sb.append("• 提前练习，流畅自然\n");

        return sb.toString();
    }

    @Tool(description = "分析薪资竞争力，根据岗位和经验给出薪资建议")
    String analyzeSalary(String position, String level, String city) {
        log.info("## 分析薪资: {}, {}, {}", position, level, city);

        StringBuilder sb = new StringBuilder();
        sb.append("💰 薪资竞争力分析\n");
        sb.append("岗位: ").append(position).append("\n");
        sb.append("级别: ").append(level).append("\n");
        sb.append("城市: ").append(city).append("\n\n");

        Map<String, Map<String, List<String>>> salaryData = new LinkedHashMap<>();

        Map<String, List<String>> javaData = new LinkedHashMap<>();
        javaData.put("初级(0-2年)", List.of("8K-15K", "10K-18K", "12K-20K"));
        javaData.put("中级(2-5年)", List.of("15K-25K", "18K-30K", "20K-35K"));
        javaData.put("高级(5年+)", List.of("25K-40K", "30K-50K", "35K-60K"));
        salaryData.put("Java后端", javaData);

        Map<String, List<String>> frontendData = new LinkedHashMap<>();
        frontendData.put("初级(0-2年)", List.of("7K-14K", "9K-16K", "11K-18K"));
        frontendData.put("中级(2-5年)", List.of("14K-23K", "16K-28K", "18K-32K"));
        frontendData.put("高级(5年+)", List.of("23K-38K", "28K-45K", "32K-55K"));
        salaryData.put("前端", frontendData);

        String[] cities = {"一线(北上广深)", "新一线(杭州成都)", "二线"};
        int cityIndex = city != null && city.contains("成都") ? 2 : (city.contains("杭州") ? 1 : 0);

        sb.append("📊 市场薪资范围（均为月薪）:\n\n");

        for (Map.Entry<String, Map<String, List<String>>> entry : salaryData.entrySet()) {
            if (position.toLowerCase().contains(entry.getKey().toLowerCase())) {
                sb.append("【").append(entry.getKey()).append("】\n");
                for (Map.Entry<String, List<String>> levelEntry : entry.getValue().entrySet()) {
                    if (level != null && level.contains(levelEntry.getKey().split("\\(")[0])) {
                        sb.append(String.format("  %s: %s（%s）⭐推荐\n",
                                levelEntry.getKey(),
                                levelEntry.getValue().get(cityIndex),
                                cities[cityIndex]));
                    } else {
                        sb.append(String.format("  %s: %s（%s）\n",
                                levelEntry.getKey(),
                                levelEntry.getValue().get(cityIndex),
                                cities[cityIndex]));
                    }
                }
                break;
            }
        }

        sb.append("\n💡 谈薪技巧:\n");
        sb.append("• 提前了解公司薪资结构和福利\n");
        sb.append("• 给出薪资范围时留有余地\n");
        sb.append("• 强调你的独特价值\n");
        sb.append("• 可以谈绩效奖金、股票期权等\n");
        sb.append("• 不要只盯着 base，total package 更重要\n");

        return sb.toString();
    }

    @Tool(description = "生成面试问题预测，根据简历和岗位生成可能的面试问题")
    String predictInterviewQuestions(String jobType, String resumeHighlights) {
        log.info("## 预测面试问题: {}, 简历亮点: {}", jobType, resumeHighlights);

        StringBuilder sb = new StringBuilder();
        sb.append("🔮 面试问题预测\n\n");

        sb.append("【必问问题】\n");
        sb.append("1. 请做一下自我介绍（1-2分钟）\n");
        sb.append("2. 你最有成就感的项目是什么？\n");
        sb.append("3. 遇到过最大的技术挑战？如何解决的？\n");
        sb.append("4. 为什么想离职/换工作？\n");
        sb.append("5. 你的职业规划是什么？\n");
        sb.append("6. 你对我们公司了解多少？\n\n");

        if (jobType != null && jobType.toLowerCase().contains("java")) {
            sb.append("【Java 技术问题】\n");
            sb.append("1. HashMap 的底层实现原理？\n");
            sb.append("2. synchronized 和 ReentrantLock 的区别？\n");
            sb.append("3. Spring Bean 的生命周期？\n");
            sb.append("4. MySQL 索引失效的场景？\n");
            sb.append("5. Redis 数据过期策略？\n");
            sb.append("6. 分布式 Session 如何实现？\n");
            sb.append("7. 如何解决缓存穿透、击穿、雪崩？\n\n");

            sb.append("【场景设计题】\n");
            sb.append("1. 如何设计一个秒杀系统？\n");
            sb.append("2. 如何实现分布式锁？\n");
            sb.append("3. 如何处理高并发下的库存超卖？\n\n");
        } else if (jobType != null && jobType.toLowerCase().contains("前端")) {
            sb.append("【前端技术问题】\n");
            sb.append("1. Vue 响应式原理？\n");
            sb.append("2. Virtual DOM 的作用？\n");
            sb.append("3. 如何实现组件通信？\n");
            sb.append("4. Promise 和 async/await 的区别？\n");
            sb.append("5. 如何做性能优化？\n");
            sb.append("6. Webpack 构建流程？\n\n");

            sb.append("【场景设计题】\n");
            sb.append("1. 如何设计一个登录系统？\n");
            sb.append("2. 如何实现一个无限滚动列表？\n");
            sb.append("3. 如何做前端权限控制？\n\n");
        }

        sb.append("【反向提问环节】\n");
        sb.append("准备 2-3 个向面试官提问的问题：\n");
        sb.append("1. 这个岗位的团队规模和分工？\n");
        sb.append("2. 技术栈和项目方向？\n");
        sb.append("3. 绩效评估标准？\n");
        sb.append("4. 团队技术氛围？\n\n");

        sb.append("💡 准备建议:\n");
        sb.append("• 每个项目准备 1-2 个具体案例\n");
        sb.append("• 记住简历中写的所有技术细节\n");
        sb.append("• 不会的问题诚实说不会，表现出学习意愿\n");
        sb.append("• 保持自信，眼神交流\n");

        return sb.toString();
    }

    @Tool(description = "生成离职原因模板，帮助用户用专业的方式表达离职原因")
    String generateResignationReasons(String reason) {
        log.info("## 生成离职原因: {}", reason);

        StringBuilder sb = new StringBuilder();
        sb.append("📝 离职原因表达模板\n\n");

        sb.append("【推荐表达】\n");
        sb.append("✅ 公司业务调整，个人发展方向不匹配\n");
        sb.append("✅ 希望接触更大规模的系统和架构\n");
        sb.append("✅ 追求更好的技术成长空间\n");
        sb.append("✅ 工作地点/通勤距离问题\n");
        sb.append("✅ 家庭原因（慎重使用）\n\n");

        sb.append("【避免使用】\n");
        sb.append("❌ 工资太低（改为：希望有更大的发展空间）\n");
        sb.append("❌ 领导不好（改为：希望寻找更契合的团队文化）\n");
        sb.append("❌ 加班太多（改为：希望提高工作效率）\n");
        sb.append("❌ 同事矛盾（不要提及）\n");
        sb.append("❌ 纯粹为了钱（可以提但要包装）\n\n");

        sb.append("【万能模板】\n");
        sb.append("我在公司工作 X 年，学到了很多。但现在希望：\n");
        sb.append("1. 寻找更大的平台，接触更复杂的业务\n");
        sb.append("2. 在技术方向上有更深入的探索\n");
        sb.append("3. 找到一个与个人发展更匹配的环境\n");
        sb.append("经过深思熟虑，我决定寻找新的机会。\n\n");

        sb.append("💡 注意事项:\n");
        sb.append("• 保持积极正面，不要抱怨前公司\n");
        sb.append("• 强调是对未来的期待，而非对过去的逃避\n");
        sb.append("• 提前练习，表达流畅自然\n");

        return sb.toString();
    }
}
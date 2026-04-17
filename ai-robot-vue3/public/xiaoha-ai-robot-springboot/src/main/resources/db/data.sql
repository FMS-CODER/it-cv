-- 简历优化知识库示例数据
-- 分类说明: 
-- - resume_template: 简历模板
-- - resume_optimization: 简历优化技巧
-- - interview_preparation: 面试准备
-- - career_advice: 职业建议
-- - technical_interview: 技术面试

-- 简历模板示例
INSERT INTO resume_knowledge_base (content, category, metadata) VALUES
('个人信息：
- 姓名：张三
- 电话：138-xxxx-xxxx
- 邮箱：zhangsan@email.com
- GitHub：github.com/zhangsan
- 博客：blog.zhangsan.com

教育背景：
- 某某大学 | 计算机科学与技术 | 本科 | 2018.09 - 2022.06

工作经历：
- 某某科技有限公司 | Java后端开发工程师 | 2022.07 - 至今
  • 负责电商平台核心模块开发，使用 Spring Boot + MyBatis
  • 优化数据库查询，QPS 提升 50%，响应时间降低 40%
  • 参与微服务架构改造，实现服务解耦
  • 编写单元测试，覆盖率达到 85%

项目经验：
- 电商订单系统重构
  • 技术栈：Spring Cloud + Redis + RocketMQ
  • 负责订单模块开发，支持高并发场景
  • 实现分布式事务，保证数据一致性

专业技能：
- Java基础：集合、多线程、JVM、IO/NIO
- 框架：Spring Boot、Spring Cloud、MyBatis
- 数据库：MySQL、Redis、MongoDB
- 中间件：RabbitMQ、Kafka、Nacos
- 工具：Git、Docker、Maven、Linux', 'resume_template', '{"type": "java_backend", "level": "junior"}'),

('简历写作技巧：
1. 使用动词开头：负责、主导、实现、优化、设计、重构
2. 量化成果：提升了X%，节省了Y时间，处理了Z数据量
3. 突出技术难点：遇到的问题、解决方案、最终结果
4. 针对性定制：针对不同岗位调整简历内容
5. 简洁有力：一页纸原则，突出重点，避免冗余', 'resume_optimization', '{"type": "writing_tips"}'),

('简历常见问题：
❌ 错误写法：
- 参与了项目开发
- 负责后端开发
- 学习了很多技术

✅ 正确写法：
- 主导电商订单模块重构，使用 Spring Cloud 微服务架构
- 优化数据库查询性能，QPS 从 500 提升到 1000
- 设计并实现分布式缓存方案，响应时间降低 60%', 'resume_optimization', '{"type": "common_mistakes"}'),

('Java 面试知识点：
基础部分：
- 集合框架：HashMap、ArrayList、ConcurrentHashMap 原理
- 多线程：线程池、synchronized、Lock、CAS、volatile
- JVM：内存结构、GC 算法、类加载机制、性能调优
- IO/NIO：BIO、NIO、Netty

框架部分：
- Spring：IOC、AOP 原理
- Spring Boot：自动配置、starter 原理
- Spring MVC：工作流程、拦截器
- MyBatis：#{} 与 ${} 区别、一级二级缓存

数据库：
- MySQL：索引、事务、锁、SQL 优化
- Redis：数据结构、持久化、缓存策略、分布式锁', 'technical_interview', '{"type": "java"}'),

('面试自我介绍模板（1-2分钟）：
面试官您好，我是张三，有2年Java后端开发经验。
之前在某某公司负责电商平台核心模块开发，主要使用 Spring Boot + Spring Cloud 技术栈。
我参与过订单系统重构、性能优化等重点项目，有高并发场景的实践经验。
我熟悉 MySQL、Redis、消息队列等中间件，对微服务架构有一定理解。
希望能加入贵公司，贡献我的技术能力。', 'interview_preparation', '{"type": "self_introduction"}'),

('常见面试问题及回答思路：
Q1：请介绍一下你做过的项目？
A：使用 STAR 法则
- S：项目背景
- T：我的任务
- A：我做了什么
- R：取得了什么成果

Q2：你遇到过最大的技术挑战是什么？
A：选择真实案例，说明问题、分析过程、解决方案

Q3：为什么想换工作？
A：职业发展、技术成长、平台机会（不要说前公司坏话）

Q4：你的职业规划是什么？
A：短期（1-2年）、中期（3-5年）、长期（5年+）', 'interview_preparation', '{"type": "common_questions"}'),

('职业发展建议 - 初级工程师（0-2年）：
1. 夯实基础：数据结构、算法、操作系统、计算机网络
2. 多写代码：每周完成1-2个小项目
3. 阅读源码：理解框架原理
4. 写技术博客：记录学习过程
5. 找 mentor：寻求前辈指导

职业发展建议 - 中级工程师（2-5年）：
1. 技术深度：深入研究某一领域
2. 架构能力：学习系统设计
3. 项目管理：推动项目落地
4. 团队协作：带领新人成长
5. 技术视野：关注行业趋势

职业发展建议 - 高级工程师（5 年+）：
1. 技术架构：系统级设计能力
2. 技术领导力：团队技术决策
3. 业务理解：技术驱动业务
4. 影响力：技术品牌建设', 'career_advice', '{"type": "career_path"}');

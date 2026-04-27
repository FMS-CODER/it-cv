package com.quanxiaoha.ai.robot.advisor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;

/**
 * @Author: 犬小哈
 * @Date: 2025/5/26 16:36
 * @Version: v1.0.0
 * @Description: 自定义日志记录 Advisor
 **/
@Slf4j
public class MyLoggerAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
        log.info("## 请求入参: {}", chatClientRequest);
        ChatClientResponse chatClientResponse = callAdvisorChain.nextCall(chatClientRequest);
        log.info("## 请求出参: {}", chatClientResponse);
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 1; // order 值越小，越先执行
    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }
}

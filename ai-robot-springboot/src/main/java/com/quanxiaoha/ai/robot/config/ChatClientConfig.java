package com.quanxiaoha.ai.robot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.Primary;

/**
 * @Author：小明
 * @Date: 2025/5/24 17:30
 * @Version: v1.0.0
 * @Description: ChatClient 配置类
 **/
@Configuration
public class ChatClientConfig {

    /**
     * 默认 ChatClient：绑定 DeepSeek。
     */
    @Bean
    @Primary
    @Description("默认 ChatClient（DeepSeek）")
    public ChatClient chatClient(@Qualifier("deepSeekChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * DashScope 专用 ChatClient。
     * 按你的命名要求保留为 dashscopechaclientmodel。
     */
    @Bean(name = "dashscopechaclientmodel")
    @Description("DashScope ChatClient")
    public ChatClient dashscopeChatClientModel(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}

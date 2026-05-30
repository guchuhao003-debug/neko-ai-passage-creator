package com.wenxi.nekoaipassage.aitest;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;

/**
 * DashScope API 接入测试
 */
@SpringBootTest
public class SpringAITest {

    @Resource
    private DashScopeChatModel dashScopeChatModel;

    @Test
    public void testChat() {
        // 同步调用
        String response = dashScopeChatModel.call("你好，请你简单介绍一下自己");
        System.out.println(response);

        // 流式调用
        Flux<ChatResponse> streamResponse = dashScopeChatModel.stream(
                new Prompt("用一句话来总结 Spring AI ")
        );
        streamResponse.subscribe(chunk -> System.out.println(chunk.getResult().getOutput().getText()));
    }

}

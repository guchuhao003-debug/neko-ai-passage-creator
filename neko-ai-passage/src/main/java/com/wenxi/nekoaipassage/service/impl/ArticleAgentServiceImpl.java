package com.wenxi.nekoaipassage.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.wenxi.nekoaipassage.constant.PromptConstant;
import com.wenxi.nekoaipassage.model.dto.article.ArticleState;
import com.wenxi.nekoaipassage.service.ArticleAgentService;
import com.wenxi.nekoaipassage.service.CosService;
import com.wenxi.nekoaipassage.service.PexelsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文章智能体编排服务
 */
@Service
@Slf4j
public class ArticleAgentServiceImpl implements ArticleAgentService {

    @Resource
    private DashScopeChatModel dashScopeChatModel;

    @Resource
    private PexelsService pexelsService;

    @Resource
    private CosService cosService;

    private static final Gson GSON = new Gson();

    /**
     * 执行完整的文章生成流程 （执行服务入口）
     *
     * @param state
     * @param streamHandler
     */
    @Override
    public void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler) {
        try {
            // 智能体 1 : 生成标题
            log.info("智能体 1 : 开始生成标题，taskId = {}", state.getTaskId());
            agent1GenerateTitle(state);
            streamHandler.accept("AGENT1_COMPLETE");

            // 智能体 2 : 生成大纲
            log.info("智能体 2 : 开始生成大纲, taskId = {}", state.getTaskId());
            agent2GenerateOutline(state);
            streamHandler.accept("AGENT2_COMPLETE");

            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept("AGENT3_COMPLETE");

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept("AGENT4_COMPLETE");

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept("AGENT5_COMPLETE");

            log.info("文章生成完成, taskId={}", state.getTaskId());
        } catch (Exception e) {
            log.error("文章生成失败，taskId = {}", state.getTaskId(), e);
            throw new RuntimeException("文章生成失败： " + e.getMessage(), e);
        }
    }

    /**
     * 智能体 1 ：负责生成标题 : 根据主题生成标题（例如主标题 + 副标题）
     *
     * @param state
     */
    private void agent1GenerateTitle(ArticleState state) {
        // 将提示词模板中的占位符替换为实际的主题词来构造一个清晰的指令，此处的 prompt 为 用户输入的指令
        String prompt = PromptConstant.AGENT1_TITLE_PROMPT
                .replace("{topic}", state.getTopic());
        // 向大模型发送一条用户消息，内容为 替换后的 prompt
        ChatResponse response = dashScopeChatModel.call(new Prompt(new UserMessage(prompt)));
        // 从响应体中获取大模型输出文本
        String content = response.getResult().getOutput().getText();

        try {
            /**
             * 将大模型的响应输出Json文本解析为 Json 对象
             * {"mainTitle":"AI新纪元","subTitle":"大模型如何重塑未来"}
             */
            ArticleState.TitleResult titleResult = GSON.fromJson(content, ArticleState.TitleResult.class);
            // 将解析出的标题对象保存到 ArticleState 中，供后续智能体（如生成正文）使用
            state.setTitle(titleResult);
            log.info("智能体 1 : 标题生成成功，mainTitle = {}", titleResult.getMainTitle());
        } catch (JsonSyntaxException e) {
            log.error("智能体1 : 标题解析失败， content = {}", content, e);
            throw new RuntimeException("标题解析异常");
        }
    }

    /**
     * 智能体 2 ：负责生成大纲
     *
     * @param state
     */
    private void agent2GenerateOutline(ArticleState state) {
        // 将提示词模板中的占位符替换为实际的主题词来构造一个清晰的指令，此处的 prompt 为 用户输入的指令
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle());

        // 调用大模型接口，来获取大模型的响应结果
        ChatResponse response = dashScopeChatModel.call(new Prompt(new UserMessage(prompt)));
        // 从响应体中获取大模型输出文本
        String content = response.getResult().getOutput().getText();

        try {
            // 解析大模型响应结果，将解析出的大纲结果对象保存到 ArticleState 中，供后续智能体（如生成正文）使用
            ArticleState.OutlineResult outlineResult = GSON.fromJson(content, ArticleState.OutlineResult.class);
            state.setOutline(outlineResult);
            log.info("智能体 2 ：大纲生成成功，sections = {}", outlineResult.getSections().size());
        } catch (JsonSyntaxException e) {
            log.error("智能体 2 ：大纲解析失败，content = {}", content, e);
            throw new RuntimeException("大纲解析失败");
        }
    }

    /**
     * 智能体 3 ： 生成文章正文 （采用流式输出）
     *
     * @param state         共享状态对象
     * @param streamHandler 函数式接口，用于实时推送流式生成的每个文本块（chunk）。调用方可以将其用作 WebSocket 发送、SSE 推送或日志输出等。
     */
    private void agent3GenerateContent(ArticleState state, Consumer<String> streamHandler) {
        /**
         * 获取大纲中的章节列表（List<Section>），每个章节包含 sectionTitle 和 summary。
         * 并且将 Json 对象转换为 Json 字符串
         * [
         * {"sectionTitle":"引言","summary":"介绍AI的发展历程"},
         * {"sectionTitle":"核心技术","summary":"讲解Transformer架构"}
         * ]
         */
        String outlineText = GSON.toJson(state.getOutline().getSections());
        // 拼接构造提示词
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText);

        // 创建正文缓冲区
        StringBuilder contentBuilder = new StringBuilder();

        // 调用流式接口，将正文内容流式输出
        Flux<ChatResponse> streamResponse = dashScopeChatModel.stream(new Prompt(new UserMessage(prompt)));
        streamResponse.subscribe(
                // onNext: 处理每个块
                response -> {
                    // 提取当前块中文本
                    String chunk = response.getResult().getOutput().getText();
                    // 追加到缓冲区
                    contentBuilder.append(chunk);
                    // 推送流式内容
                    streamHandler.accept("AGENT3_STREAMING: " + chunk);
                },
                // onError: 异常处理
                error -> {
                    log.error("智能体 3 ： 正文生成失败", error);
                    throw new RuntimeException("正文生成失败" + error.getMessage());
                },
                // onComplete: 完成时回调
                () -> {
                    state.setContent(contentBuilder.toString());
                    log.info("智能体 3 ： 正文生成完成，length = {}", contentBuilder.length());
                }
        );
        // 等待流式输出完成： blockLast() 是 Flux 的阻塞方法，会等待直到所有流式元素都发射完毕（或发生错误），确保方法同步返回
        streamResponse.blockLast();
    }

    /**
     * 智能体 4 ：负责分析配图请求
     *
     * @param state
     */
    private void agent4AnalyzeImageRequirements(ArticleState state) {
        // 拼接构造提示词
        String prompt = PromptConstant.AGENT4_IMAGE_REQUIREMENTS_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{content}", state.getContent());
        // 调用模型获取响应结果
        ChatResponse response = dashScopeChatModel.call(new Prompt(new UserMessage(prompt)));
        // 从响应结果中获取模型输出文本
        String content = response.getResult().getOutput().getText();

        try {
            /**
             * 解析 JSON 为 List 对象
             */
            List<ArticleState.ImageRequirement> imageRequirements = GSON.fromJson(
                    content,
                    // 构造一个 Type 对象，表示 List<ImageRequirement> 类型，告诉 Gson 如何反序列化
                    new TypeToken<List<ArticleState.ImageRequirement>>() {
                    }.getType()
            );
            state.setImageRequirements(imageRequirements);
            log.info("智能体 4 ：配图需求分析成功， count = {}", imageRequirements.size());
        } catch (JsonSyntaxException e) {
            log.error("智能体 4 ：配图需求分析失败，content = {}", content, e);
            throw new RuntimeException("配图需求解析失败");
        }
    }

    /**
     * 智能体 5 : 生成配图 （串行执行）
     *
     * @param state
     * @param streamHandler
     */
    private void agent5GenerateImages(ArticleState state, Consumer<String> streamHandler) {

        // 初始化结果列表
        List<ArticleState.ImageResult> imageResults = new ArrayList<>();

        // 遍历每个配图需求
        // 从状态中获取需求列表（智能体 4 生成）。
        for (ArticleState.ImageRequirement requirement : state.getImageRequirements()) {
            log.info("智能体 5 ： 开始检索配图, position = {}, keywords = {}",
                    requirement.getPosition(), requirement.getKeywords());

            // 调用 Pexels API 检索图片
            String imageUrl = pexelsService.searchImage(requirement.getKeywords());

            // 降级策略
            // 标记点，用于区分是 Pexels API 还是 PICSUM API 降级策略
            String method = "PEXELS";
            if (imageUrl == null) {
                imageUrl = pexelsService.getFallbackImage(requirement.getPosition());
                method = "PICSUM";
                log.warn("智能体 5 ： Pexels API 检索图片失败，使用降级策略，position = {}", requirement.getPosition());
            }

            // 使用图片直接 URL （ MVP 阶段不上传到 COS，简化流程）
            String finalImageUrl = cosService.useDirectUrl(imageUrl);

            // 创建配图结果
            ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
            imageResult.setPosition(requirement.getPosition());
            imageResult.setUrl(finalImageUrl);
            imageResult.setMethod(method);
            imageResult.setKeywords(requirement.getKeywords());
            imageResult.setDescription(requirement.getType());
            // 将单个结果存入列表
            imageResults.add(imageResult);
            // 推送单张配图完成
            streamHandler.accept("IMAGE_COMPLETE: " + GSON.toJson(imageResult));
            log.info("智能体 5 : 配图检索成功，position = {}, method = {}", requirement.getPosition(), method);
        }
        state.setImages(imageResults);
        log.info("智能体 5 : 所有配图生成完成，count = {}", imageResults.size());
    }

}

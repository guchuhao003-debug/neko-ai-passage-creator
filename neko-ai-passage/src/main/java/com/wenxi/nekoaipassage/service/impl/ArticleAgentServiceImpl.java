package com.wenxi.nekoaipassage.service.impl;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.wenxi.nekoaipassage.constant.PromptConstant;
import com.wenxi.nekoaipassage.enums.ImageMethodEnum;
import com.wenxi.nekoaipassage.enums.SseMessageTypeEnum;
import com.wenxi.nekoaipassage.model.dto.article.ArticleState;
import com.wenxi.nekoaipassage.service.ArticleAgentService;
import com.wenxi.nekoaipassage.service.CosService;
import com.wenxi.nekoaipassage.service.ImageServiceStrategy;
import com.wenxi.nekoaipassage.utils.GsonUtils;
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
    private ImageServiceStrategy imageServiceStrategy;

    @Resource
    private CosService cosService;

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
            streamHandler.accept(SseMessageTypeEnum.AGENT1_COMPLETE.getValue());

            // 智能体 2 : 生成大纲
            log.info("智能体 2 : 开始生成大纲, taskId = {}", state.getTaskId());
            agent2GenerateOutline(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT2_COMPLETE.getValue());

            // 智能体3：生成正文（流式输出）
            log.info("智能体3：开始生成正文, taskId={}", state.getTaskId());
            agent3GenerateContent(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT3_COMPLETE.getValue());

            // 智能体4：分析配图需求
            log.info("智能体4：开始分析配图需求, taskId={}", state.getTaskId());
            agent4AnalyzeImageRequirements(state);
            streamHandler.accept(SseMessageTypeEnum.AGENT4_COMPLETE.getValue());

            // 智能体5：生成配图
            log.info("智能体5：开始生成配图, taskId={}", state.getTaskId());
            agent5GenerateImages(state, streamHandler);
            streamHandler.accept(SseMessageTypeEnum.AGENT5_COMPLETE.getValue());

            // 图文合成：将配图插入正文
            log.info("开始图文合成，taskId = {}", state.getTaskId());
            mergeImagesIntoContent(state);
            streamHandler.accept(SseMessageTypeEnum.MERGE_COMPLETE.getValue());

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
        // 调用 callLLM() 向大模型发送一条用户消息，内容为 替换后的 prompt,从响应体中获取大模型输出文本
        String content = callLLM(prompt);
         /**
         * 将大模型的响应输出Json文本解析为 Json 对象
         * {"mainTitle":"AI新纪元","subTitle":"大模型如何重塑未来"}
         */
        ArticleState.TitleResult titleResult =parseJsonResponse(content, ArticleState.TitleResult.class,"标题");
        // 将解析出的标题对象保存到 ArticleState 中，供后续智能体（如生成正文）使用
            state.setTitle(titleResult);
            log.info("智能体 1 : 标题生成成功，mainTitle = {}", titleResult.getMainTitle());
    }

    /**
     * 智能体 2 ：负责生成大纲 （流式输出）
     *
     * @param state
     */
    private void agent2GenerateOutline(ArticleState state, Consumer<String> streamHandler) {
        // 将提示词模板中的占位符替换为实际的主题词来构造一个清晰的指令，此处的 prompt 为 用户输入的指令
        String prompt = PromptConstant.AGENT2_OUTLINE_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle());

        // 调用 callLLMWithStreaming() 获取流式输出文本
        String content = callLLMWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT2_STREAMING);
        ArticleState.OutlineResult outlineResult = parseJsonResponse(content, ArticleState.OutlineResult.class, "大纲");
        // 将解析出的大纲对象保存到 ArticleState 中，供后续智能体使用
        state.setOutline(outlineResult);
        log.info("智能体 2 : 大纲生成成功，secetions = {}", outlineResult.getSections().size());
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
        String outlineText = GsonUtils.toJson(state.getOutline().getSections());
        // 拼接构造提示词
        String prompt = PromptConstant.AGENT3_CONTENT_PROMPT
                .replace("{mainTitle}", state.getTitle().getMainTitle())
                .replace("{subTitle}", state.getTitle().getSubTitle())
                .replace("{outline}", outlineText);

        // 调用 callLLMWithStreaming() 获取流式输出文本
        String content = callLLMWithStreaming(prompt, streamHandler, SseMessageTypeEnum.AGENT3_STREAMING);
        // 将解析出的正文对象保存到 ArticleState 中，供后续智能体使用
        state.setContent(content);
        log.info("智能体 3：正文生成成功，length = {}", content.length());
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
        // 调用 callLLM() 从模型获取响应结果,并且在响应结果中获取模型输出文本
        String content = callLLM(prompt);
        List<ArticleState.ImageRequirement> imageRequirements = parseJsonListResponse(
                content,
                new TypeToken<List<ArticleState.ImageRequirement>>() {
                },
                "配图需求"
        );
        // 将解析出的配图需求保存到 ArticleState 中，供后续智能体使用
        state.setImageRequirements(imageRequirements);
        log.info("智能体4：配图需求分析成功，count = {}", imageRequirements.size());
    }

    /**
     * 智能体 5 : 生成配图 （串行执行，流式输出）
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
            String imageSource = requirement.getImageSource();
            log.info("智能体 5 ： 开始获取配图, position = {}, imageSource = {}, keywords = {}",
                    requirement.getPosition(), imageSource, requirement.getKeywords());

            // 使用策略模式根据 imageSource 来选择对应的图片服务
            ImageServiceStrategy.ImageResult result = imageServiceStrategy.getImage(
                    imageSource,
                    requirement.getKeywords(),
                    requirement.getPrompt());

            // 从图片获取结果中获取 URL
            String imageUrl = result.getUrl();
            ImageMethodEnum method = result.getMethod();

            // 降级策略
            if (!result.isSuccess()) {
                imageUrl = imageServiceStrategy.getFallbackImage(requirement.getPosition());
                method = ImageMethodEnum.PICSUM;
                log.warn("智能体5 ：图片获取失败，使用降级方案，position = {}, originalSource = {}", requirement.getPosition(), imageSource);
            }

            // 使用图片直接 URL （ MVP 阶段不上传到 COS，简化流程）
            String finalImageUrl = cosService.useDirectUrl(imageUrl);

            // 创建配图结果
            ArticleState.ImageResult imageResult = buildImageResult(requirement, finalImageUrl, method);
            // 将单个结果存入列表
            imageResults.add(imageResult);
            // 推送单张配图完成
            String imageCompleteMessage = SseMessageTypeEnum.IMAGE_COMPLETE.getStreamingPrefix() + GsonUtils.toJson(imageResult);
            streamHandler.accept(imageCompleteMessage);
            log.info("智能体5 : 配图获取成功，position = {}, method = {}", requirement.getPosition(), method.getValue());
        }
        // 将解析出的图片列表对象保存到 ArticleState 中，供后续智能体使用
        state.setImages(imageResults);
        log.info("智能体5 : 所有配图生成完成，count = {}", imageResults.size());
    }

    /**
     * 图文合成： 将配图插入正文对应位置
     * @param state
     */
    private void mergeImagesIntoContent(ArticleState state) {
        // 获取正文内容
        String content = state.getContent();
        // 获取图片列表
        List<ArticleState.ImageResult> images = state.getImages();

        // 如果没有图片，则直接设置完整内容
        if (images == null || images.isEmpty()) {
            state.setFullContent(content);
            return;
        }

        StringBuilder fullContent = new StringBuilder();

        // 按行处理正文，在章节标题后插入对应图片
        String[] lines = content.split("\n");
        for (String line : lines) {
            fullContent.append(line).append("\n");
            // 检查是否是章节标题 (假设章节标题以 "## " 开头)
            if (line.startsWith("## ")) {
                String sectionTitle = line.substring(3).trim();
                insertImageAfterSection(fullContent, images, sectionTitle);
            }
        }
        state.setFullContent(fullContent.toString());
        log.info("图文合成完成，fullContentLength = {}", fullContent.length());
    }

    // ======= 封装方法 ========

    /**
     * 调用 LLM （同步调用）
     * @param prompt
     * @return
     */
    private String callLLM(String prompt) {
        // 向大模型发送一条用户消息，内容为 替换后的 prompt
        ChatResponse response = dashScopeChatModel.call(new Prompt(new UserMessage(prompt)));
        // 从响应体中获取大模型输出文本
        return response.getResult().getOutput().getText();
    }

    /**
     * 调用 LLM （流式调用）
     * @param prompt
     * @param streamHandler
     * @param messageTypeEnum
     * @return
     */
    private String callLLMWithStreaming(String prompt, Consumer<String> streamHandler, SseMessageTypeEnum messageTypeEnum) {
        // 缓冲区实例
        StringBuilder contentBuilder = new StringBuilder();

        // 流式输出
        Flux<ChatResponse> streamResponse = dashScopeChatModel.stream(new Prompt(new UserMessage(prompt)));
        streamResponse
                .doOnNext(response -> {
                    String chunk = response.getResult().getOutput().getText();
                    if (chunk != null && !chunk.isEmpty()) {
                        contentBuilder.append(chunk);
                        streamHandler.accept(messageTypeEnum.getStreamingPrefix() + chunk);
                    }
                })
                .doOnError(error -> log.error("LLM 流式调用失败，messageType = {}", messageTypeEnum,error))
                .blockLast();

        return contentBuilder.toString();
    }

    /**
     * 解析 JSON 响应
     * @param content
     * @param clazz
     * @param name
     * @return
     * @param <T>
     */
    private <T> T parseJsonResponse(String content, Class<T> clazz, String name) {
        try {
            return GsonUtils.fromJson(content, clazz);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败，content = {}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 解析 JSON 列表响应
     * @param content
     * @param typeToken
     * @param name
     * @return
     * @param <T>
     */
    private <T> T parseJsonListResponse(String content, TypeToken<T> typeToken, String name) {
        try {
            return GsonUtils.fromJson(content, typeToken);
        } catch (JsonSyntaxException e) {
            log.error("{}解析失败，content = {}", name, content, e);
            throw new RuntimeException(name + "解析失败");
        }
    }

    /**
     * 构建配图结果
     * @param requirement
     * @param imageUrl
     * @param methodEnum
     * @return
     */
    private ArticleState.ImageResult buildImageResult(ArticleState.ImageRequirement requirement,
                                                      String imageUrl,
                                                      ImageMethodEnum methodEnum) {
        ArticleState.ImageResult imageResult = new ArticleState.ImageResult();
        imageResult.setPosition(requirement.getPosition());
        imageResult.setUrl(imageUrl);
        imageResult.setMethod(methodEnum.getValue());
        imageResult.setKeywords(requirement.getKeywords());
        imageResult.setSectionTitle(requirement.getSectionTitle());
        imageResult.setDescription(requirement.getType());
        return imageResult;
    }


    /**
     * 在章节标题后方插入对应图片
     * @param fullContent
     * @param images
     * @param sectionTitle
     */
    private void insertImageAfterSection(StringBuilder fullContent, List<ArticleState.ImageResult> images, String sectionTitle) {
        for (ArticleState.ImageResult image : images) {
            if (image.getPosition() > 1 &&
                    image.getSectionTitle() != null &&
                    sectionTitle.contains(image.getSectionTitle().trim())) {
                fullContent.append("\n![").append(image.getDescription())
                        .append("](").append(image.getUrl()).append(")\n");
                break;
            }
        }
    }

}

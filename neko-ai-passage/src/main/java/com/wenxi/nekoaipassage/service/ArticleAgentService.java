package com.wenxi.nekoaipassage.service;

import com.wenxi.nekoaipassage.model.dto.article.ArticleState;

import java.util.function.Consumer;

/**
 * AI 文章智能体编排服务
 */
public interface ArticleAgentService {

    /**
     * 执行完整的文章生成流程 （执行服务入口）
     *
     * @param state
     * @param streamHandler
     */
    void executeArticleGeneration(ArticleState state, Consumer<String> streamHandler);

}

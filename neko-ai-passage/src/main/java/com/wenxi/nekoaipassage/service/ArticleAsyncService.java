package com.wenxi.nekoaipassage.service;

/**
 * 文章异步任务调用
 */
public interface ArticleAsyncService {

    /**
     * 异步执行文章生成
     *
     * @param taskId
     * @param topic
     */
    void executeArticleGenerationByAsync(String taskId, String topic);


}

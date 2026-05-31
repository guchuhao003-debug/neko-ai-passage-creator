package com.wenxi.nekoaipassage.constant;

/**
 * 文章相关常量类
 */
public interface ArticleConstant {

    /**
     * Pexels API 请求地址
     */
    String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    /**
     * Pexels 图片方向 ： 横向
     */
    String PEXELS_ORIENTATION_LANDSCAPE = "landscape";

    /**
     * Pexels 每页返回数量
     */
    int PEXELS_PER_PAGE = 1;

    /**
     * Picsum 随机图片 URL 模板
     */
    String PICSUM_URL_TEMPLATE = "https://picsum.photos/800/600?random=%d";


}

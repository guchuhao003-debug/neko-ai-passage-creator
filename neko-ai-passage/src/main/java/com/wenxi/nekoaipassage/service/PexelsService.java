package com.wenxi.nekoaipassage.service;


public interface PexelsService {

    /**
     * 根据关键字检索图片
     *
     * @param keywords 搜索关键词
     * @return 图片 URL
     */
    String searchImage(String keywords);

    /**
     * 降级方案：使用 picsum 随机图片
     *
     * @param position 位置序号
     * @return 图片 URL
     */
    String getFallbackImage(int position);

}

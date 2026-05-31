package com.wenxi.nekoaipassage.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wenxi.nekoaipassage.config.PexelsConfig;
import com.wenxi.nekoaipassage.enums.ImageMethodEnum;
import com.wenxi.nekoaipassage.service.ImageSearchService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import static com.wenxi.nekoaipassage.constant.ArticleConstant.*;

/**
 * Pexels 图片检索服务 （降级策略： Picnum 随机图片）
 */
@Service
@Slf4j
public class PexelsServiceImpl implements ImageSearchService {

    @Resource
    private PexelsConfig pexelsConfig;

    private final OkHttpClient httpClient = new OkHttpClient();


    /**
     * 根据关键词检索图片
     *
     * @param keywords 搜索关键词
     * @return 图片 URL （即 large 字段的值）
     */
    @Override
    public String searchImage(String keywords) {
        try {
            String url = buildSearchUrl(keywords);

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", pexelsConfig.getApiKey())
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                // 检查响应是否成功
                if (!response.isSuccessful()) {
                    log.error("Pexels API 调用失败: {}", response.code());
                    return null;
                }

                // 如果响应成功，则解析响应体
                String responseBody = response.body().string();
                return extractImageUrl(responseBody, keywords);
            }
        } catch (Exception e) {
            log.error("Pexels API 调用异常", e);
            return null;
        }
    }

    /**
     * 获取图片方案
     * @return
     */
    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.PEXELS;
    }


    /**
     * 降级方案：使用 picsum 随机图片 -> https://picsum.photos/
     * 当 Pexels 调用失败时，则使用降级方案，使用随机图片
     *
     * @param position 位置序号
     * @return 图片 URL
     */
    @Override
    public String getFallbackImage(int position) {
        /**
         * 800/600 表示请求图片的宽度为 800 像素，高度为 600 像素。
         * random=1 表示请求随机生成一张图片，1 是随机数，每次请求都会生成不同的图片。
         */
        return String.format(PICSUM_URL_TEMPLATE, position);
    }

    /**
     * 构建搜索 URL
     * @param keywords  搜索关键字
     * @return  完整的搜索 URL
     */
    private String buildSearchUrl(String keywords) {
        return String.format("%s?query=%s&per_page=%d&orientation=%s",
                PEXELS_API_URL,
                keywords,
                PEXELS_PER_PAGE,
                PEXELS_ORIENTATION_LANDSCAPE
                );
    }

    /**
     * 从响应体中提取图片 URL
     * @param responseBody
     * @param keywords
     * @return
     */
    private String extractImageUrl(String responseBody, String keywords) {
        // 将响应Json格式的字符串解析为 Json 对象
        JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
        JsonArray photos = jsonObject.getAsJsonArray("photos");
        // 检查 photos 数组是否为空（即是否存在图片）
        if (photos.isEmpty()) {
            log.warn("Pexels 未检索到图片： {}", keywords);
            return null;
        }
        /**
         * 图片对象格式如下：
         * {
         *   "id": 12345,
         *   "width": 2000,
         *   "height": 1333,
         *   "src": {
         *     "original": "...",
         *     "large": "...",
         *     "medium": "...",
         *     "small": "..."
         *   },
         *   "photographer": "..."
         * }
         */
        // 获取第一张图片对象
        JsonObject photo = photos.get(0).getAsJsonObject();
        // 从图片对象中获取 src 对象
        JsonObject src = photo.getAsJsonObject("src");
        // 从 src 对象中获取并返回 large 字段的值，即大图 URL
        return src.get("large").getAsString();

    }
}

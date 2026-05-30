package com.wenxi.nekoaipassage.service.impl;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wenxi.nekoaipassage.config.PexelsConfig;
import com.wenxi.nekoaipassage.service.PexelsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class PexelsServiceImpl implements PexelsService {

    @Resource
    private PexelsConfig pexelsConfig;

    private final OkHttpClient httpClient = new OkHttpClient();

    private static final String PEXELS_API_URL = "https://api.pexels.com/v1/search";

    /**
     * 根据关键词检索图片
     *
     * @param keywords 搜索关键词
     * @return 图片 URL （即 large 字段的值）
     */
    @Override
    public String searchImage(String keywords) {
        try {
            String url = PEXELS_API_URL + "?query=" + keywords + "&per_page=1&orientation=landscape";

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
                // 将响应Json格式的字符串解析为 Json 对象
                JsonObject jsonObject = JsonParser.parseString(responseBody).getAsJsonObject();
                /*
                 响应信息结构如下：
                 {
                     "page": 1,
                     "per_page": 15,
                     "photos": [ ... ],
                     "total_results": 150
                  }
                * */
                // 从解析的 Json 对象中获取 photos 数组
                JsonArray photos = jsonObject.getAsJsonArray("photos");
                // 检查 photos 数组是否为空（即是否存在图片）
                if (photos.size() == 0) {
                    log.warn("Pexels 未检索到图片：{}", keywords);
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
        } catch (Exception e) {
            log.error("Pexels API 调用异常", e);
            return null;
        }
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
        return "https://picsum.photos/800/600?random=" + position;
    }
}

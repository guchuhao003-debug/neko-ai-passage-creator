package com.wenxi.nekoaipassage.service;

import com.google.genai.Client;
import com.google.genai.types.*;
import com.wenxi.nekoaipassage.config.NanoBananaConfig;
import com.wenxi.nekoaipassage.enums.ImageMethodEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.UUID;

import static com.wenxi.nekoaipassage.constant.ArticleConstant.PICSUM_URL_TEMPLATE;

/**
 * Nano Banana (Gemini 原生图片生成) 服务
 * 使用 Gemini 2.5 flash image / Gemini 3 pro Image 模型生成图片
 *
 */
@Service
@Slf4j
public class NanoBananaService implements ImageSearchService {

    @Resource
    private NanoBananaConfig nanoBananaConfig;

    @Resource
    private CosService cosService;

    /**
     * 搜索图片
     * @param keywords 搜索关键词
     * @return
     */
    @Override
    public String searchImage(String keywords) {
        // 对于 NanoBanana， keywords 就是生图 prompt
        return generateImage(keywords);
    }

    /**
     * 根据提示词生成图片
     * @param prompt
     * @return
     */
    public String generateImage(String prompt) {
        try {

            // 使用 Builder 显式设置 API Key
            Client genaiClient = Client.builder()
                    .apiKey(nanoBananaConfig.getApiKey())
                    .build();

            try {
                // 构建图片配置
                ImageConfig.Builder imageConfigBuilder = ImageConfig.builder()
                        .aspectRatio(nanoBananaConfig.getAspectRatio());

                // Gemini 3 Pro Image 支持更高分辨率
                String model = nanoBananaConfig.getModel();
                if (model != null && model.contains("gemini-3-pro")) {
                    imageConfigBuilder.imageSize(nanoBananaConfig.getImageSize());
                }

                // 构成生成配置
                GenerateContentConfig config = GenerateContentConfig.builder()
                        .responseModalities("TEXT", "IMAGE")
                        .imageConfig(imageConfigBuilder.build())
                        .build();

                log.info("Nano Banana 开始图片生成，model = {}, prompt = {}",
                        model, prompt);

                // 调用 Gemini API 生成图片
                GenerateContentResponse response = genaiClient.models.generateContent(
                        model != null ? model : "gemini-2.5-flash-image",
                        prompt,
                        config
                );

                // 从响应中提取图片数据
                if (response.parts() != null) {
                    for (Part part : response.parts()) {
                        if (part.inlineData().isPresent()) {
                            var blob = part.inlineData().get();
                            if (blob.data().isPresent()) {
                                byte[] imageBytes = blob.data().get();
                                String mimeType = blob.mimeType().orElse("image/png");

                                log.info("Nano Banana 图片生成成功，size = {} bytes, mimeType = {}",
                                        imageBytes.length, mimeType);

                                // 上传图片到 COS 并返回 URL
                                return uploadImageToCos(imageBytes, mimeType);
                            }
                        }
                    }
                }
                log.warn("Nano Banana 未生成图片，prompt = {}", prompt);
                return null;
            } finally {
                genaiClient.close();
            }
        } catch (Exception e) {
            log.error("Nano Banana 生成图片异常，prompt = {}", prompt, e);
            return null;
        }
    }

    /**
     * 构建 Gemini SDK 客户端代理配置。
     *
     * @return 客户端配置，未配置代理时返回 null
     */
    private ClientOptions buildClientOptions() {
        String proxyHost = nanoBananaConfig.getProxyHost();
        Integer proxyPort = nanoBananaConfig.getProxyPort();
        if (proxyHost == null || proxyHost.isBlank() || proxyPort == null || proxyPort <= 0) {
            return null;
        }

        // 代理类型默认使用 HTTP，配置为 SOCKS 时走 SOCKS 代理。
        ProxyType.Known proxyType = "SOCKS".equalsIgnoreCase(nanoBananaConfig.getProxyType())
                ? ProxyType.Known.SOCKS
                : ProxyType.Known.HTTP;

        return ClientOptions.builder()
                .proxyOptions(ProxyOptions.builder()
                        .type(proxyType)
                        .host(proxyHost)
                        .port(proxyPort)
                        .build())
                .build();
    }

    /**
     * 上传图片字节数据到 COS
     * @param imageBytes
     * @param mimeType
     * @return
     */
    private String uploadImageToCos(byte[] imageBytes, String mimeType) {
        try {
            // 生成临时文件名
            String extension = mimeType.contains("jpeg") || mimeType.contains("jpg") ? ".jpg" : ".png";
            String fileName = "nano-banana/" + System.currentTimeMillis() + "_" +
                    UUID.randomUUID().toString().substring(0,8) + extension;

            // 使用 CosService 上传（需要先转换为 URL 或者直接上传字节）
            // 由于 CosService 目前只支持 URL 下载上传，这里直接使用 base64 data URL
            // 或者扩展为 CosService 支持字节上传

            // 临时方案：将图片转换为 base64 data URL （前端可直接使用）
            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            String dataUrl = "data:" + mimeType + ";base64," + base64;

            log.info("Nano Banana 图片已生成 Data URL, length = {}", dataUrl.length());
            return dataUrl;
        } catch (Exception e) {
            log.error("上传 Nano Banana 图片失败", e);
            return null;
        }
    }

    /**
     * 获取图片方式
     * @return
     */
    @Override
    public ImageMethodEnum getMethod() {
        return ImageMethodEnum.NANO_BANANA;
    }

    /**
     * 获取备用图片
     * @param position 位置序号
     * @return
     */
    @Override
    public String getFallbackImage(int position) {
        return String.format(PICSUM_URL_TEMPLATE, position);
    }

}

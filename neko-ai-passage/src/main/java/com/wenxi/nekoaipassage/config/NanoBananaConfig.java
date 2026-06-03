package com.wenxi.nekoaipassage.config;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "gemini")
@Data
public class NanoBananaConfig {

    /**
     *  Gemini API Key
     */
    private String apiKey;

    /**
     *  模型名称
     *  gemini-2.5-flash-image: 速度快，适合高吞吐低延迟
     *  gemini-3-pro-image-preview : 专业级，支持高级推理和高分辨率
     */
    private String model = "gemini-2.5-flash-image";

    /**
     * 图片宽高比
     * 支持: 1:1, 2:3, 3:2, 3:4, 4:3, 4:5, 5:4, 9:16, 16:9, 21:9
     */
    private String aspectRatio = "16:9";

    /**
     * 图片分辨率 （仅支持 gemini-3-pro-image-preview）
     * 支持：1K、2K、4K
     */
    private String imageSize = "1K";

    /**
     * 输出图片格式
     * 支持：image/png、image/jpeg
     */
    private String outputMimeType = "image/png";

    /**
     * Gemini API 请求超时时间，单位秒
     */
    private Integer timeoutSeconds = 60;

    /**
     * Gemini API 代理主机，不配置则直连
     */
    private String proxyHost;

    /**
     * Gemini API 代理端口，不配置则直连
     */
    private Integer proxyPort;

    /**
     * Gemini API 代理类型，支持 HTTP、SOCKS
     */
    private String proxyType = "HTTP";

}

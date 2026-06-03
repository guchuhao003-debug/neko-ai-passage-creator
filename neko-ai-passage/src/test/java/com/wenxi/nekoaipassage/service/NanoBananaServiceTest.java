package com.wenxi.nekoaipassage.service;

import com.wenxi.nekoaipassage.config.NanoBananaConfig;
import com.wenxi.nekoaipassage.enums.ImageMethodEnum;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Nano Banana 图片生成服务测试
 * 此测试会实际调用 Gemini API， 请确保配置了有效的 API Key
 */
@SpringBootTest
@ActiveProfiles("local")
class NanoBananaServiceTest {

    @Resource
    private NanoBananaService nanoBananaService;

    @Resource
    private NanoBananaConfig nanoBananaConfig;

    @BeforeEach
    void setUp() {
        assertNotNull(nanoBananaService, "NanoBananaService 未注入");
        assertNotNull(nanoBananaConfig, "NanoBananaConfig 未注入");
    }

    @Test
    void testGetMethod() {
        assertEquals(ImageMethodEnum.NANO_BANANA, nanoBananaService.getMethod());
    }

    @Test
    void testGetFallbackImage() {
        String fallback = nanoBananaService.getFallbackImage(1);
        assertNotNull(fallback);
        assertTrue(fallback.contains("picsum.photos"));
    }

    @Test
    void testGenerateImage() {
        // 检查是否配置了 API Key
        if (nanoBananaConfig.getApiKey() == null || nanoBananaConfig.getApiKey().isEmpty()) {
            System.out.println("跳过测试：未配置 Gemini API Key");
            return;
        }

        String prompt = "A simple minimalist illustration of a cute robot reading a book, " +
                "blue and white color scheme, clean design, digital art style";

        System.out.println("开始生成图片，prompt: " + prompt);
        System.out.println("使用模型：" + nanoBananaConfig.getModel());

        String imageUrl = nanoBananaService.generateImage(prompt);

        System.out.println("生成结果：" + (imageUrl != null ? "成功" : "失败"));
        if (imageUrl != null) {
            // 如果是 data URL, 只打印前 100 个字符
            if (imageUrl.startsWith("data:")) {
                System.out.println("图片类型: Data URL");
                String preview = imageUrl.substring(0, Math.min(100, imageUrl.length()));
                System.out.println("图片预览：" + preview + "...");
            } else {
                System.out.println("图片 URL: " + imageUrl);
            }
        }
        assertNotNull(imageUrl, "图片生成失败");
        assertTrue(imageUrl.startsWith("data:image/") || imageUrl.startsWith("http"),
                "图片 URL 格式不正确");
    }

    /**
     * 脱敏 API Key，避免测试日志泄露完整密钥。
     *
     * @param apiKey 原始 API Key
     * @return 脱敏后的 API Key
     */
    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() < 8) {
            return "未配置";
        }
        return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
    }

    @Test
    void testSearchImage() {
        // 检查是否配置了 API Key
        if (nanoBananaConfig.getApiKey() == null || nanoBananaConfig.getApiKey().isEmpty()) {
            System.out.println("跳过测试：未配置 API Key");
            return;
        }
        String keywords = "futuristic city skyline sunset";

        System.out.println("开始通过 searchImage 图片， keywords = " + keywords);

        String imageUrl = nanoBananaService.searchImage(keywords);
        System.out.println("生成结果：" + (imageUrl != null ? "成功" : "失败"));

        assertNotNull(imageUrl, "图片生成失败");
    }
}

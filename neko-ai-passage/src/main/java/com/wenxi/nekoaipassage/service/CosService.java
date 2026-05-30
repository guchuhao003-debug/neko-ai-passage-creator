package com.wenxi.nekoaipassage.service;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectRequest;
import com.qcloud.cos.region.Region;
import com.wenxi.nekoaipassage.config.CosConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 腾讯云 COS 服务
 */
@Service
@Slf4j
public class CosService {

    /**
     * 注入腾讯云 COS 配置文件
     */
    @Resource
    private CosConfig cosConfig;

    /**
     * 声明 COS 客户端
     */
    private COSClient cosClient;

    /**
     * 声明并初始化一个 OkHttpClient 实例
     */
    private final OkHttpClient httpClient = new OkHttpClient();

    /**
     * @PostConstruct 注解，确保在 Bean 的依赖注入完成后自动执行客户端的创建
     * 在 Spring 容器启动时，根据配置文件（或 CosConfig 配置类）中提供的 SecretId、SecretKey、Region 等信息，
     * 构建一个可供后续上传/下载文件使用的 COSClient 实例。
     */
    @PostConstruct
    public void init() {
        // 1、创建 COS 凭证对象 （通过 SecretId 和 SecretKey 来获取凭证对象 ）
        COSCredentials cred = new BasicCOSCredentials(cosConfig.getSecretId(), cosConfig.getSecretKey());
        // 2、获取存储桶的地域对象
        Region region = new Region(cosConfig.getRegion());
        // 3、创建 COS 客户端配置对象
        ClientConfig clientConfig = new ClientConfig();
        // setHttpProtocol(HttpProtocol.https)：强制使用 HTTPS 协议访问 COS（推荐，保证传输安全）。
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 4、根据 COS 凭证对象和 COS 客户端配置对象来初始化 COS 客户端对象
        cosClient = new COSClient(cred, clientConfig);
    }

    /**
     * 上传图片到 COS
     *
     * @param imageUrl 图片 URL
     * @param folder   文件夹
     * @return COS 图片 URL
     */
    public String uploadImage(String imageUrl, String folder) {
        try {
            // 下载图片
            // 1.构建 Http GET 请求对象
            Request request = new Request.Builder().url(imageUrl).build();
            // 2. 同步执行请求，获取 response 请求响应对象
            try (Response response = httpClient.newCall(request).execute()) {
                // 检查响应是否成功
                if (!response.isSuccessful()) {
                    log.error("下载图片失败: {}", imageUrl);
                    // 失败则直接采用降级方案，直接返回原始 URL，不再尝试上传到 COS
                    return imageUrl;
                }

                // 响应成功时，则从响应体中获取图片字节数组
                byte[] imageBytes = response.body().bytes();

                // 生成文件名
                String fileName = folder + "/" + UUID.randomUUID() + ".jpg";

                // 上传到 COS
                /**
                 * ByteArrayInputStream 是字节输入流，它把内存中的一个缓冲区当作数据源来处理，把字节数组包装成输入流。
                 */
                try (InputStream inputStream = new ByteArrayInputStream(imageBytes)) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    // 必须设置，否则 COS 无法正确接收
                    metadata.setContentLength(imageBytes.length);
                    // 设置对象类型
                    metadata.setContentType("image/jpeg");

                    // 创建并封装上传请求：存储桶名、对象键（路径）、输入流、元数据
                    PutObjectRequest putObjectRequest = new PutObjectRequest(
                            cosConfig.getBucket(), fileName, inputStream, metadata
                    );
                    cosClient.putObject(putObjectRequest);
                    // 返回访问 URL
                    return String.format("https://%s.cos.%s.myqcloud.com/%s", cosConfig.getBucket(), cosConfig.getRegion(), fileName);
                }
            }
        } catch (IOException e) {
            log.error("上传图片到 COS 失败，", e);
            // 降级方案：直接返回原始 URL
            return imageUrl;
        }
    }

    /**
     * 直接使用图片 URL ( 不上传到 COS )
     *
     * @param imageUrl 图片 URL
     * @return 图片 URL
     */
    public String useDirectUrl(String imageUrl) {
        return imageUrl;
    }


}

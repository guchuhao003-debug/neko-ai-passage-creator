package com.wenxi.nekoaipassage.manager;


import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class SseEmitterManager {

    /**
     * 存储所有的 SseEmitter
     */
    private final Map<String, SseEmitter> emitterMap = new ConcurrentHashMap<>();

    /**
     * 超时时间 ： 30分钟
     */
    private static final long TIMEOUT = 30 * 60 * 1000;

    /**
     * 创建 SseEmitter 并存入 emitterMap 集中管理
     *
     * @param taskId 任务 id
     * @return SseEmitter
     */
    public SseEmitter createEmitter(String taskId) {
        // 初始化已设置超时时间的 SseEmitter 实例
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        // 设置超时回调
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时，taskId = {}", taskId);
            // 从 emitterMap 集合中移除超时的 SseEmitter
            emitterMap.remove(taskId);
        });

        // 设置完成回调
        emitter.onCompletion(() -> {
            log.info("SSE 连接完成，taskId = {}", taskId);
            // 从 emitterMap 集合中移除完成的 SseEmitter
            emitterMap.remove(taskId);
        });

        // 设置错误回调
        emitter.onError((e) -> {
            log.error("SSE 连接错误，taskId = {}", taskId, e);
            emitterMap.remove(taskId);
        });

        // 正常情况下，将 SseEmitter 存入 emitterMap 集合
        emitterMap.put(taskId, emitter);
        log.info("SSE 连接已创建, taskId = {}", taskId);

        return emitter;
    }

    /**
     * 发送消息
     *
     * @param taskId  任务 Id
     * @param message 消息内容
     */
    public void send(String taskId, String message) {
        // 根据 taskId 获取 SseEmitter
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.warn("SseEmitter 不存在，taskId = {}", taskId);
            return;
        }

        try {
            emitter.send(SseEmitter.event()
                    .data(message)
                    .reconnectTime(3000)
            );
            log.debug("SSE 消息发送成功，taskId = {}, message = {}", taskId, message);
        } catch (IOException e) {
            log.error("SSE 消息发送失败，taskId = {}, message = {}", taskId, message, e);
            // 从 emitterMap 中移除与 taskId 相关联的 SseEmitter 对象
            emitterMap.remove(taskId);
        }
    }

    /**
     * 完成连接
     *
     * @param taskId 任务 Id
     */
    public void complete(String taskId) {
        // 根据 taskId 获取 SseEmitter 实例
        SseEmitter emitter = emitterMap.get(taskId);
        if (emitter == null) {
            log.warn("SseEmitter 不存在，taskId = {}", taskId);
            return;
        }

        try {
            // 完成连接
            emitter.complete();
            log.info("SSE 连接已完成，taskId = {}", taskId);
        } catch (Exception e) {
            log.error("SSE 连接完成失败，taskId = {}", taskId, e);
        } finally {
            // 不管连接完成是否成功，都需要移除 taskId 相关联的 SseEmitter 对象
            emitterMap.remove(taskId);
        }
    }

    /**
     * 检查 Emitter 是否存在
     *
     * @param taskId 任务 Id
     * @return true/false
     */
    public boolean exists(String taskId) {
        return emitterMap.containsKey(taskId);
    }

}

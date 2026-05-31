package com.wenxi.nekoaipassage.service;

import com.wenxi.nekoaipassage.model.entity.User;

/**
 * 配额服务
 *
 */
public interface QuotaService {

    /**
     * 检查用户是否有足够的配额
     *
     * @param user  用户
     * @return  是否有配额
     */
    boolean hasQuata(User user);

    /**
     * 消耗配额（扣减 1 次）
     *
     * @param user  用户
     */
    void consumeQuota(User user);

    /**
     * 检查并消耗配额（原子操作）
     * 如果配额不足则抛异常
     *
     * @param user  用户
     */
    void checkAndConsumeQuota(User user);
}

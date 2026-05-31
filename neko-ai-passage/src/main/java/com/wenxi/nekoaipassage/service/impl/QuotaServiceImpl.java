package com.wenxi.nekoaipassage.service.impl;

import com.wenxi.nekoaipassage.constant.UserConstant;
import com.wenxi.nekoaipassage.exception.BusinessException;
import com.wenxi.nekoaipassage.exception.ErrorCode;
import com.wenxi.nekoaipassage.mapper.UserMapper;
import com.wenxi.nekoaipassage.model.entity.User;
import com.wenxi.nekoaipassage.service.QuotaService;
import com.wenxi.nekoaipassage.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 配额服务实现类
 *
 * 并发安全说明 ：
 * 1、使用数据库原子更新 （UPDATE ... SET quota = quota - 1 WHERE quota > 0） 避免竞态条件
 * 2、通过影响行数判断操作是否成功，无需先查询再更新
 * 3、使用 @Transactional 事务注解来确保配额扣减与后续操作的一致性
 *
 */

@Service
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    /**
     * 检查用户是否有足够的配额
     *
     * @param user  用户
     * @return  是否有配额
     */
    @Override
    public boolean hasQuata(User user) {
        // 管理员无限配额
        if (isAdmin(user)) {
            return true;
        }
        // 从数据库查询最新配额，避免使用缓存的旧数据
        User freshUser = userService.getById(user.getId());
        if (freshUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        Integer quota = freshUser.getQuota();
        return quota != null && quota > 0;
    }

    /**
     * 消耗配额（扣减 1 次）
     *
     * @param user  用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(User user) {
        // 管理员不消耗配额
        if (isAdmin(user)) {
            return;
        }

        // 使用原子更新： UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        // 通过影响行数判断是否成功，避免并发问题
        int affectedRows = userMapper.decrementQuota(user.getId());

        if (affectedRows > 0) {
            log.info("用户配额已消耗，userId = {}",user.getId());
        } else {
            log.warn("用户配额扣减失败（可能配额不足或并发冲突），userId = {}", user.getId());
        }
    }

    /**
     * 检查并消耗配额（原子操作）
     * 如果配额不足则抛异常
     *
     * @param user  用户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsumeQuota(User user) {
        // 管理员跳过检查
        if (isAdmin(user)) {
            return;
        }

        // 使用原子更新，检查与消费合并为一个原子操作
        // UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        int affectedRow = userMapper.decrementQuota(user.getId());

        if (affectedRow == 0) {
            // 影响行数为 0 ，说明配额不足（已被其他请求消耗）
            throw new BusinessException(ErrorCode.OPERATION_ERROR,"配额不足，无法创建文章");
        }

        log.info("用户配额检查并消耗成功，userId = {}", user.getId());
    }

    /**
     * 判断是否为管理员
     * @param user
     * @return
     */
    private boolean isAdmin(User user) {
        return UserConstant.ADMIN_ROLE.equals(user.getUserRole());
    }
}

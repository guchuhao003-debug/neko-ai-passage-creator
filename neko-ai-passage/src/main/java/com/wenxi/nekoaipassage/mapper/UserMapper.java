package com.wenxi.nekoaipassage.mapper;


import com.mybatisflex.core.BaseMapper;
import com.wenxi.nekoaipassage.model.entity.User;
import io.lettuce.core.dynamic.annotation.Param;
import org.apache.ibatis.annotations.Update;

/**
 * @author kk
 * @description 针对表【user(用户)】的数据库操作Mapper
 * @createDate 2026-05-21 21:49:34
 * @Entity generator.domain.User
 */
public interface UserMapper extends BaseMapper<User> {

    /**
     * 原子扣减用户配额
     * 使用 quota > 0 条件确保并发安全，避免超扣
     *
     * @param userId    用户 ID
     * @return  影响行数， 1 表示成功，0 表示配额不足
     */
    @Update("UPDATE user SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
    int decrementQuota(@Param("userId") Long userId);

}





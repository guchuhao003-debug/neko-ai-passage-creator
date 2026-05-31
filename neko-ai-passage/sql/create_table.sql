-- 创建库
create
    database if not exists ai_passage_creator;
use
    ai_passage_creator;

-- 用户表
create table if not exists user
(
    id
                 bigint
        auto_increment
        comment
            'id'
        primary
            key,
    userAccount
                 varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount
        (
         userAccount
            ),
    INDEX idx_userName
        (
         userName
            )
) comment '用户' collate = utf8mb4_unicode_ci;

-- 创建库
create
    database if not exists ai_passage_creator;
use
    ai_passage_creator;

-- 用户表
create table if not exists user
(
    id
                 bigint
        auto_increment
        comment
            'id'
        primary
            key,
    userAccount
                 varchar(256)                           not null comment '账号',
    userPassword varchar(512)                           not null comment '密码',
    userName     varchar(256)                           null comment '用户昵称',
    userAvatar   varchar(1024)                          null comment '用户头像',
    userProfile  varchar(512)                           null comment '用户简介',
    userRole     varchar(256) default 'user'            not null comment '用户角色：user/admin',
    editTime     datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint      default 0                 not null comment '是否删除',
    UNIQUE KEY uk_userAccount
        (
         userAccount
            ),
    INDEX idx_userName
        (
         userName
            )
) comment '用户' collate = utf8mb4_unicode_ci;

-- 初始化测试数据（密码是 12345678，MD5 加密 + 盐值 yupi）
INSERT INTO user (id, userAccount, userPassword, userName, userAvatar, userProfile, userRole)
VALUES (1, 'admin', '10670d38ec32fa8102be6a37f8cb52bf', '管理员', 'https://www.codefather.cn/logo.png', '系统管理员',
        'admin'),
       (2, 'user', '10670d38ec32fa8102be6a37f8cb52bf', '普通用户', 'https://www.codefather.cn/logo.png',
        '我是一个普通用户', 'user'),
       (3, 'test', '10670d38ec32fa8102be6a37f8cb52bf', '测试账号', 'https://www.codefather.cn/logo.png',
        '这是一个测试账号', 'user');


-- 文章表
create table if not exists article
(
    id            bigint auto_increment comment 'id' primary key,
    taskId        varchar(64)                           not null comment '任务ID（UUID）',
    userId        bigint                                not null comment '用户ID',
    topic         varchar(500)                          not null comment '选题',
    mainTitle     varchar(200)                          null comment '主标题',
    subTitle      varchar(300)                          null comment '副标题',
    outline       json                                  null comment '大纲（JSON格式）',
    content       text                                  null comment '正文（Markdown格式）',
    fullContent   text                                  null comment '完整图文（Markdown格式，含配图）',
    coverImage    varchar(512)                          null comment '封面图 URL',
    images        json                                  null comment '配图列表（JSON数组）',
    status        varchar(20) default 'PENDING'         not null comment '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    errorMessage  text                                  null comment '错误信息',
    createTime    datetime    default CURRENT_TIMESTAMP not null comment '创建时间',
    completedTime datetime                              null comment '完成时间',
    updateTime    datetime    default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint     default 0                 not null comment '是否删除',
    UNIQUE KEY uk_taskId (taskId),
    INDEX idx_userId (userId),
    INDEX idx_status (status),
    INDEX idx_createTime (createTime),
    INDEX idx_userId_status (userId, status)
) comment '文章表' collate = utf8mb4_unicode_ci;

-- updateTime 添加默认值和自动更新属性
ALTER TABLE `article`
    MODIFY COLUMN `updateTime` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP;


-- 用户配额升级脚本
-- 为已有数据库添加 quota 字段

-- 添加 quota 字段（如果不存在）
ALTER TABLE user ADD COLUMN quota int default 5 not null comment '剩余配额' AFTER userRole;

-- 为已有用户设置默认配额
UPDATE user SET quota = 5 WHERE quota IS NULL;
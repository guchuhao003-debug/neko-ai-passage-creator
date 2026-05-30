package com.wenxi.nekoaipassage.controller;


import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.wenxi.neko_ai_agent.exception.BusinessException;
import com.wenxi.nekoaipassage.common.BaseResponse;
import com.wenxi.nekoaipassage.common.DeleteRequest;
import com.wenxi.nekoaipassage.common.ResultUtils;
import com.wenxi.nekoaipassage.exception.ErrorCode;
import com.wenxi.nekoaipassage.exception.ThrowUtils;
import com.wenxi.nekoaipassage.manager.SseEmitterManager;
import com.wenxi.nekoaipassage.mapper.ArticleMapper;
import com.wenxi.nekoaipassage.model.dto.article.ArticleCreateRequest;
import com.wenxi.nekoaipassage.model.dto.article.ArticleQueryRequest;
import com.wenxi.nekoaipassage.model.entity.Article;
import com.wenxi.nekoaipassage.model.entity.User;
import com.wenxi.nekoaipassage.model.vo.ArticleVO;
import com.wenxi.nekoaipassage.service.ArticleAsyncService;
import com.wenxi.nekoaipassage.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/article")
@Tag(name = "文章接口")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleMapper articleMapper;

    @Resource
    private ArticleAsyncService articleAsyncService;

    @Resource
    private SseEmitterManager sseEmitterManager;

    @Resource
    private UserService userService;

    /**
     * 创建文章任务
     *
     * @param articleCreateRequest
     * @param request
     * @return
     */
    @PostMapping("/create")
    @Operation(summary = "创建文章任务")
    public BaseResponse<String> createArticle(@RequestBody ArticleCreateRequest articleCreateRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(articleCreateRequest == null, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(articleCreateRequest.getTopic() == null || articleCreateRequest.getTopic().trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "选题不能为空");
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 生成任务 Id
        String taskId = IdUtil.simpleUUID();
        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(articleCreateRequest.getTopic());
        article.setStatus("PENDING");
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        // 插入文章记录到数据库
        articleMapper.insert(article);
        // 异步执行文章生成
        articleAsyncService.executeArticleGenerationByAsync(taskId, articleCreateRequest.getTopic());
        log.info("文章任务已创建，taskId = {}, userId = {}", taskId, loginUser.getId());
        return ResultUtils.success(taskId);
    }

    /**
     * SSE 进度推送
     *
     * @param taskId
     * @return
     */
    @GetMapping("/progress/{taskId}")
    @Operation(summary = "获取文章生成进度（SSE）")
    public SseEmitter getProgress(@PathVariable String taskId) {
        // 校验参数
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        // 检查任务是否存在
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId)
        );
        ThrowUtils.throwIf(article == null, ErrorCode.PARAMS_ERROR, "任务不存在");
        // 创建 SSE 连接
        SseEmitter emitter = sseEmitterManager.createEmitter(taskId);
        log.info("SSE 连接已建立, taskId = {}", taskId);
        return emitter;
    }

    /**
     * 获取文章详情
     *
     * @param taskId
     * @param request
     * @return
     */
    @GetMapping("/{taskId}")
    @Operation(summary = "获取文章详情")
    public BaseResponse<ArticleVO> getArticle(@PathVariable String taskId, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 查询文章记录
        Article article = articleMapper.selectOneByQuery(
                QueryWrapper.create().eq("taskId", taskId)
        );
        ThrowUtils.throwIf(article == null, ErrorCode.PARAMS_ERROR, "文章不存在");
        // 校验权限： 只能查看自己的文章 （管理员除外）
        if (!article.getUserId().equals(loginUser.getId()) && !"admin".equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 封装返回脱敏结果
        return ResultUtils.success(ArticleVO.objToVo(article));
    }

    /**
     * 分页查询文章列表
     *
     * @param articleQueryRequest
     * @param request
     * @return
     */
    @PostMapping("/list")
    @Operation(summary = "分页查询文章列表")
    public BaseResponse<Page<ArticleVO>> listArticle(@RequestBody ArticleQueryRequest articleQueryRequest, HttpServletRequest request) {
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 获取参数
        long current = articleQueryRequest.getPageNum();
        int size = articleQueryRequest.getPageSize();
        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .orderBy("createTime", false);
        // 非管理员只能查看自己的文章
        if (!"admin".equals(loginUser.getUserRole())) {
            queryWrapper.eq("userId", loginUser.getId());
        } else if (articleQueryRequest.getUserId() != null) {
            queryWrapper.eq("userId", articleQueryRequest.getUserId());
        }
        // 按状态筛选
        if (articleQueryRequest.getStatus() != null && !articleQueryRequest.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", articleQueryRequest.getStatus());
        }
        // 分页查询
        Page<Article> articlePage = articleMapper.paginate(
                new Page<>(current, size), queryWrapper
        );
        // 转换 VO 视图
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        return ResultUtils.success(articleVOPage);
    }

    /**
     * 删除文章
     *
     * @param deleteRequest
     * @param request
     * @return
     */
    @PostMapping("/delete")
    @Operation(summary = "删除文章")
    public BaseResponse<Boolean> deleteArticle(@RequestBody DeleteRequest deleteRequest, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAMS_ERROR);
        // 获取登录用户
        User loginUser = userService.getLoginUser(request);
        // 查询获取文章记录
        Article article = articleMapper.selectOneById(deleteRequest.getId());
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR);
        // 校验权限
        if (!article.getUserId().equals(loginUser.getId()) && !"admin".equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 删除文章 （逻辑删除）
        article.setIsDelete(1);
        boolean result = articleMapper.update(article) > 0;
        return ResultUtils.success(result);
    }

}
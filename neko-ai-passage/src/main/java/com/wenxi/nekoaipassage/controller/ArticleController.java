package com.wenxi.nekoaipassage.controller;


import com.mybatisflex.core.paginate.Page;
import com.wenxi.nekoaipassage.common.BaseResponse;
import com.wenxi.nekoaipassage.common.DeleteRequest;
import com.wenxi.nekoaipassage.common.ResultUtils;
import com.wenxi.nekoaipassage.exception.ErrorCode;
import com.wenxi.nekoaipassage.exception.ThrowUtils;
import com.wenxi.nekoaipassage.manager.SseEmitterManager;
import com.wenxi.nekoaipassage.model.dto.article.ArticleCreateRequest;
import com.wenxi.nekoaipassage.model.dto.article.ArticleQueryRequest;
import com.wenxi.nekoaipassage.model.entity.User;
import com.wenxi.nekoaipassage.model.vo.ArticleVO;
import com.wenxi.nekoaipassage.service.ArticleAsyncService;
import com.wenxi.nekoaipassage.service.ArticleService;
import com.wenxi.nekoaipassage.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Resource
    private ArticleService articleService;

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
        // 检查并消耗配额 + 创建文章任务 （在同一事务中）
        String taskId = articleService.createArticleTaskWithQuotaCheck(articleCreateRequest.getTopic(), loginUser);
        // 异步执行文章生成
        articleAsyncService.executeArticleGenerationByAsync(taskId, articleCreateRequest.getTopic());
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
    public SseEmitter getProgress(@PathVariable String taskId, HttpServletRequest request) {
        // 校验参数
        ThrowUtils.throwIf(taskId == null || taskId.trim().isEmpty(),
                ErrorCode.PARAMS_ERROR, "任务 ID 不能为空");
        // 校验权限
        User loginUser = userService.getLoginUser(request);
        articleService.getArticleDetail(taskId, loginUser);
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
        ArticleVO articleVO = articleService.getArticleDetail(taskId, loginUser);
        // 封装返回脱敏结果
        return ResultUtils.success(articleVO);
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
        // 分页查询文章列表
        Page<ArticleVO> articleVOPage = articleService.listArticleByPage(articleQueryRequest, loginUser);
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
        // 执行删除服务
        boolean result = articleService.deleteArticle(deleteRequest.getId(), loginUser);
        return ResultUtils.success(result);
    }

}
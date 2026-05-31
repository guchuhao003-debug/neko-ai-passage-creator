package com.wenxi.nekoaipassage.service.impl;

import cn.hutool.core.util.IdUtil;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.wenxi.nekoaipassage.constant.UserConstant;
import com.wenxi.nekoaipassage.enums.ArticleStatusEnum;
import com.wenxi.nekoaipassage.exception.BusinessException;
import com.wenxi.nekoaipassage.exception.ErrorCode;
import com.wenxi.nekoaipassage.exception.ThrowUtils;
import com.wenxi.nekoaipassage.mapper.ArticleMapper;
import com.wenxi.nekoaipassage.model.dto.article.ArticleQueryRequest;
import com.wenxi.nekoaipassage.model.dto.article.ArticleState;
import com.wenxi.nekoaipassage.model.entity.Article;
import com.wenxi.nekoaipassage.model.entity.User;
import com.wenxi.nekoaipassage.model.vo.ArticleVO;
import com.wenxi.nekoaipassage.service.ArticleService;
import com.wenxi.nekoaipassage.service.QuotaService;
import com.wenxi.nekoaipassage.utils.GsonUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文章服务
 */
@Service
@Slf4j
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    @Resource
    private QuotaService quotaService;

    /**
     * 创建文章任务
     *
     * @param topic     选题
     * @param loginUser 当前登录用户
     * @return 任务ID
     */
    @Override
    public String createArticleTask(String topic, User loginUser) {
        // 生成任务 ID
        String taskId = IdUtil.simpleUUID();

        // 创建文章记录
        Article article = new Article();
        article.setTaskId(taskId);
        article.setUserId(loginUser.getId());
        article.setTopic(topic);
        article.setStatus(ArticleStatusEnum.PENDING.getValue());
        article.setCreateTime(LocalDateTime.now());

        this.save(article);

        log.info("文章任务已创建，taskId = {}, userId = {}", taskId, loginUser.getId());
        return taskId;
    }

    /**
     * 创建文章任务（带配额检查）
     * 将配额扣减和任务创建放在同一事务中，确保原子性
     *
     * @param topic     选题
     * @param loginUser 当前登录用户
     * @return 任务ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)  //开启事务
    public String createArticleTaskWithQuotaCheck(String topic, User loginUser) {
        // 在同一事务中: 先扣配额，再创建任务
        // 如果任务创建失败，配额会自动回滚
        quotaService.checkAndConsumeQuota(loginUser);
        return createArticleTask(topic, loginUser);
    }

    /**
     * 根据任务ID获取文章
     *
     * @param taskId 任务ID
     * @return 文章实体
     */
    @Override
    public Article getByTaskId(String taskId) {
        return this.getOne(
                QueryWrapper.create().eq("taskId", taskId)
        );
    }

    /**
     * 获取文章详情（带权限校验）
     *
     * @param taskId    任务ID
     * @param loginUser 当前登录用户
     * @return 文章VO
     */
    @Override
    public ArticleVO getArticleDetail(String taskId, User loginUser) {
        Article article = getByTaskId(taskId);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        // 校验权限：只能查看自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);
        return ArticleVO.objToVo(article);
    }

    /**
     * 分页查询文章列表
     *
     * @param request   查询请求
     * @param loginUser 当前登录用户
     * @return 分页结果
     */
    @Override
    public Page<ArticleVO> listArticleByPage(ArticleQueryRequest request, User loginUser) {
        long current = request.getPageNum();
        long size = request.getPageSize();

        // 构建查询条件
        QueryWrapper queryWrapper = QueryWrapper.create()
                .eq("isDelete", 0)
                .orderBy("createTime", false);

        // 非管理员只能查看自己的文章
        if (!UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            queryWrapper.eq("userId",loginUser.getId());
        } else if (request.getUserId() != null) {
            queryWrapper.eq("userId", request.getUserId());
        }

        // 按状态筛选
        if (request.getStatus() != null && !request.getStatus().trim().isEmpty()) {
            queryWrapper.eq("status", request.getStatus());
        }

        // 分页查询
        Page<Article> articlePage = this.page(new Page<>(current, size), queryWrapper);

        // 转换为 VO
        return convertToVOPage(articlePage);
    }

    /**
     * 删除文章（带权限校验）
     *
     * @param id        文章ID
     * @param loginUser 当前登录用户
     * @return 是否成功
     */
    @Override
    public boolean deleteArticle(Long id, User loginUser) {
        Article article = this.getById(id);
        ThrowUtils.throwIf(article == null, ErrorCode.NOT_FOUND_ERROR, "文章不存在");
        // 校验权限: 只能删除自己的文章（管理员除外）
        checkArticlePermission(article, loginUser);
        // 逻辑删除
        return this.removeById(id);
    }

    /**
     * 更新文章状态
     *
     * @param taskId       任务ID
     * @param status       状态枚举
     * @param errorMessage 错误信息（可选）
     */
    @Override
    public void updateArticleStatus(String taskId, ArticleStatusEnum status, String errorMessage) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在，taskId = {}", taskId);
            return;
        }
        article.setStatus(status.getValue());
        article.setErrorMessage(errorMessage);
        this.updateById(article);
        log.info("文章状态已更新，taskId = {}, status = {}", taskId, status.getValue());
    }

    /**
     * 保存文章内容
     *
     * @param taskId 任务ID
     * @param state  文章状态对象
     */
    @Override
    public void saveArticleContent(String taskId, ArticleState state) {
        Article article = getByTaskId(taskId);

        if (article == null) {
            log.error("文章记录不存在，taskId = {}", taskId);
            return;
        }

        article.setMainTitle(state.getTitle().getMainTitle());
        article.setSubTitle(state.getTitle().getSubTitle());
        article.setOutline(GsonUtils.toJson(state.getOutline().getSections()));
        article.setContent(state.getContent());
        article.setFullContent(state.getFullContent());

        // 保存封面图 URL （从 images 列表中提取 position = 1 的图片 URL）
        if (state.getImages() != null && !state.getImages().isEmpty()) {
            ArticleState.ImageResult cover = state.getImages().stream()
                    .filter(img -> img.getPosition() != null && img.getPosition() == 1)
                    .findFirst()
                    .orElse(null);
            if (cover != null && cover.getUrl() != null) {
                article.setCoverImage(cover.getUrl());
            }
        }
        article.setImages(GsonUtils.toJson(state.getImages()));
        article.setCompletedTime(LocalDateTime.now());

        this.updateById(article);
        log.info("文章保存成功，taskId = {}", taskId);
    }

    /**
     * 校验文章权限
     *
     * @param article
     * @param loginUser
     */
    private void checkArticlePermission(Article article, User loginUser) {
        if (!article.getUserId().equals(loginUser.getId())
            && !UserConstant.ADMIN_ROLE.equals(loginUser.getUserRole())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
    }

    /**
     * 将文章分页结果转换为 VO 分页
     * @param articlePage
     * @return
     */
    private Page<ArticleVO> convertToVOPage(Page<Article> articlePage) {
        Page<ArticleVO> articleVOPage = new Page<>();
        articleVOPage.setPageNumber(articlePage.getPageNumber());
        articleVOPage.setPageSize(articlePage.getPageSize());
        articleVOPage.setTotalRow(articlePage.getTotalRow());

        List<ArticleVO> articleVOList = articlePage.getRecords().stream()
                .map(ArticleVO::objToVo)
                .collect(Collectors.toList());
        articleVOPage.setRecords(articleVOList);

        return articleVOPage;
    }


}

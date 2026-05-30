package com.wenxi.nekoaipassage.aop;

import com.wenxi.neko_ai_agent.exception.BusinessException;
import com.wenxi.nekoaipassage.annotation.AuthCheck;
import com.wenxi.nekoaipassage.enums.UserRoleEnum;
import com.wenxi.nekoaipassage.exception.ErrorCode;
import com.wenxi.nekoaipassage.model.entity.User;
import com.wenxi.nekoaipassage.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class AuthInterceptor {

    @Resource
    private UserService userService;

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        // 获取当前登录用户
        User loginUser = userService.getLoginUser(request);
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        // 不需要权限，则直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        // 必须有这个权限，才可提供给
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new com.wenxi.neko_ai_agent.exception.BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 要求必须要有管理员权限，但当前用户没有
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        // 通过权限校验，放行
        return joinPoint.proceed();

    }

}

package com.campus.auction.interceptor;

import com.campus.auction.common.Result;
import com.campus.auction.common.ResultCode;
import com.campus.auction.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

/**
 * 登录拦截器：校验 Authorization: Bearer <token>，
 * 校验通过后把 openid 放入 request attribute，供 Controller 用 @RequestAttribute 取用
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_OPENID = "openid";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 预检请求放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String auth = request.getHeader("Authorization");
        if (auth == null || !auth.startsWith(BEARER_PREFIX)) {
            writeUnauthorized(response, ResultCode.UNAUTHORIZED);
            return false;
        }

        String token = auth.substring(BEARER_PREFIX.length());
        try {
            String openid = jwtUtil.parseOpenid(token);
            request.setAttribute(ATTR_OPENID, openid);
            return true;
        } catch (JwtException e) {
            writeUnauthorized(response, ResultCode.TOKEN_INVALID);
            return false;
        }
    }

    /** 未授权时写回统一 JSON 响应（HTTP 200 + 业务码 2001/2002，前端按 code 处理） */
    private void writeUnauthorized(HttpServletResponse response, ResultCode resultCode) throws Exception {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(Result.fail(resultCode)));
    }
}

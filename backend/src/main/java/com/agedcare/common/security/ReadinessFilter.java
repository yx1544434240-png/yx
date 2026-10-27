package com.agedcare.common.security;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
/** 初始化管理员、迁移旧照片完成前不接收业务流量；私有图片的错误响应同样禁止缓存。 */
@Component
public class ReadinessFilter extends OncePerRequestFilter {
    private volatile boolean ready;
    @EventListener public void ready(ApplicationReadyEvent ignored){ready=true;}
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        if(request.getRequestURI().contains("/media/private/")){
            response.setHeader("Cache-Control","no-store");response.setHeader("Pragma","no-cache");
        }
        if(!ready){
            response.setStatus(503);response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":503,\"message\":\"服务正在初始化，请稍后重试\"}");
            return;
        }
        chain.doFilter(request,response);
    }
}

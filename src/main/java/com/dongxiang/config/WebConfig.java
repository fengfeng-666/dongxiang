package com.dongxiang.config;

import com.dongxiang.auth.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.time.Duration;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final StringRedisTemplate redis;
    public WebConfig(StringRedisTemplate redis) { this.redis = redis; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
                String token = req.getHeader("Authorization");
                if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
                if (token != null && token.matches("[a-f0-9]{32}")) {
                    String key = "auth:token:" + token;
                    String id = redis.opsForValue().get(key);
                    if (id != null) { CurrentUser.set(Long.parseLong(id)); redis.expire(key, Duration.ofMinutes(30)); }
                }
                String path = req.getRequestURI();
                boolean publicPath = path.startsWith("/api/auth/") || (req.getMethod().equals("POST") && path.matches("/api/orders/[a-f0-9]{32}/payment-callback")) || (req.getMethod().equals("GET") && (path.startsWith("/api/venues") || path.startsWith("/api/posts") || path.startsWith("/api/vouchers")));
                if (!publicPath && CurrentUser.get() == null) { res.sendError(401, "请先登录"); return false; }
                return true;
            }
            @Override public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) { CurrentUser.clear(); }
        });
    }
}

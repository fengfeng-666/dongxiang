package com.dongxiang.auth;

import com.dongxiang.common.Api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final StringRedisTemplate redis; private final JdbcTemplate db; private final boolean expose;
    private final SecureRandom random = new SecureRandom();
    public AuthController(StringRedisTemplate redis, JdbcTemplate db, @Value("${app.expose-dev-code:false}") boolean expose) { this.redis=redis; this.db=db; this.expose=expose; }
    public record Phone(@NotBlank @Pattern(regexp="1[3-9]\\d{9}") String phone) {}
    public record Login(@NotBlank @Pattern(regexp="1[3-9]\\d{9}") String phone, @NotBlank @Pattern(regexp="\\d{6}") String code) {}
    @PostMapping("/code") public Api<?> code(@Valid @RequestBody Phone p) {
        String throttle="auth:throttle:"+p.phone();
        if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(throttle,"1",Duration.ofSeconds(60)))) throw new IllegalStateException("请求过于频繁");
        String code=String.format("%06d",random.nextInt(1_000_000));
        redis.opsForValue().set("auth:code:"+p.phone(),code,Duration.ofMinutes(2));
        // Production requires an SMS provider; do not log or expose codes by default.
        return Api.ok(expose ? Map.of("code",code) : Map.of("message","验证码已生成；请配置短信服务"));
    }
    @PostMapping("/login") public Api<?> login(@Valid @RequestBody Login p) {
        String key="auth:code:"+p.phone();
        Long accepted=redis.execute(new org.springframework.data.redis.core.script.DefaultRedisScript<>("if redis.call('get',KEYS[1]) == ARGV[1] then redis.call('del',KEYS[1]); return 1 else return 0 end",Long.class), java.util.List.of(key),p.code());
        if (!Long.valueOf(1).equals(accepted)) throw new IllegalArgumentException("验证码错误或已过期");
        db.update("INSERT INTO app_user(phone,nickname) VALUES (?,?) ON DUPLICATE KEY UPDATE phone=VALUES(phone)",p.phone(),"运动爱好者"+p.phone().substring(7));
        Long id=db.queryForObject("SELECT id FROM app_user WHERE phone=?",Long.class,p.phone());
        String token=UUID.randomUUID().toString().replace("-",""); redis.opsForValue().set("auth:token:"+token,id.toString(),Duration.ofMinutes(30));
        return Api.ok(Map.of("token",token,"userId",id));
    }
    @PostMapping("/logout") public Api<?> logout(@RequestHeader(value="Authorization",required=false) String token) {
        if (token != null) { if (token.startsWith("Bearer ")) token=token.substring(7); redis.delete("auth:token:"+token); } return Api.ok(null);
    }
}

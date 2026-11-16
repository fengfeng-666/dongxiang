package com.dongxiang.order;

import com.dongxiang.auth.CurrentUser;
import com.dongxiang.common.Api;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class OrderController {
    static final String STREAM="seckill:{orders}:stream";
    private final JdbcTemplate db; private final StringRedisTemplate redis; private final OrderTransactions tx; private final String callbackSecret;
    private final DefaultRedisScript<Long> reserve=new DefaultRedisScript<>();
    public OrderController(JdbcTemplate db,StringRedisTemplate redis,OrderTransactions tx,@Value("${PAYMENT_CALLBACK_SECRET:}") String secret) { this.db=db; this.redis=redis; this.tx=tx; this.callbackSecret=secret; }
    @PostConstruct void init() { reserve.setLocation(new ClassPathResource("lua/reserve.lua")); reserve.setResultType(Long.class); }
    @GetMapping("/vouchers") public Api<?> vouchers() { return Api.ok(db.queryForList("SELECT id,venue_id,title,price_cents,stock,start_at,end_at FROM voucher ORDER BY id")); }
    @PostMapping("/vouchers/{id}/orders") public Api<?> reserve(@PathVariable long id) {
        long user=CurrentUser.require();
        Integer active=db.queryForObject("SELECT count(*) FROM voucher WHERE id=? AND start_at<=NOW() AND end_at>NOW()",Integer.class,id);
        if(active==null || active==0) throw new IllegalStateException("活动不存在或不在抢购时间内");
        String order=UUID.randomUUID().toString().replace("-","");
        Long result=redis.execute(reserve,List.of("seckill:{orders}:stock:"+id,"seckill:{orders}:buyers:"+id,STREAM),Long.toString(user),order,Long.toString(id));
        if (result==null || result==3) throw new IllegalStateException("活动库存尚未初始化");
        if (result==1) throw new IllegalStateException("库存不足"); if(result==2) throw new IllegalStateException("每人限购一单");
        return Api.ok(Map.of("orderId",order,"status","ACCEPTED"));
    }
    @GetMapping("/orders/{id}") public Api<?> get(@PathVariable String id) { List<Map<String,Object>> rows=db.queryForList("SELECT id,voucher_id,user_id,status,created_at,expires_at FROM voucher_order WHERE id=? AND user_id=?",id,CurrentUser.require()); return rows.isEmpty()?Api.error("订单尚未创建或不存在"):Api.ok(rows.get(0)); }
    // This is a shared-secret integration hook, not a complete payment gateway signature implementation.
    @PostMapping("/orders/{id}/payment-callback") public Api<?> payment(@PathVariable String id,@RequestHeader(value="X-Callback-Secret",required=false) String supplied) {
        if(callbackSecret.isBlank() || supplied==null || !MessageDigest.isEqual(callbackSecret.getBytes(StandardCharsets.UTF_8),supplied.getBytes(StandardCharsets.UTF_8))) throw new IllegalArgumentException("支付回调认证失败");
        return Api.ok(Map.of("paid",tx.pay(id)));
    }
}

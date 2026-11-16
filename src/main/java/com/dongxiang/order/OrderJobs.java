package com.dongxiang.order;

import jakarta.annotation.PostConstruct;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class OrderJobs {
    private static final Logger log=LoggerFactory.getLogger(OrderJobs.class); private static final String GROUP="order-relay", CONSUMER="relay";
    private final StringRedisTemplate redis; private final RocketMQTemplate mq; private final RedissonClient redisson; private final JdbcTemplate db; private final OrderTransactions tx;
    private final DefaultRedisScript<Long> release=new DefaultRedisScript<>();
    public OrderJobs(StringRedisTemplate redis,RocketMQTemplate mq,RedissonClient redisson,JdbcTemplate db,OrderTransactions tx) { this.redis=redis;this.mq=mq;this.redisson=redisson;this.db=db;this.tx=tx; }
    @PostConstruct void init() { release.setLocation(new ClassPathResource("lua/release.lua")); release.setResultType(Long.class); }
    @Scheduled(fixedDelay=1000) public void relay() {
        RLock lock=redisson.getLock("lock:order-relay"); if(!lock.tryLock()) return;
        try {
            try { redis.opsForStream().createGroup(OrderController.STREAM,ReadOffset.from("0"),GROUP); }
            catch (Exception e) {
                if (e.getMessage()!=null && e.getMessage().contains("BUSYGROUP")) { /* already created */ }
                else if (Boolean.FALSE.equals(redis.hasKey(OrderController.STREAM))) return;
                else if (!e.toString().contains("BUSYGROUP")) throw e;
            }
            var options=StreamReadOptions.empty().count(20);
            List<MapRecord<String,Object,Object>> records=redis.opsForStream().read(Consumer.from(GROUP,CONSUMER),options,StreamOffset.create(OrderController.STREAM,ReadOffset.from("0")));
            if(records==null || records.isEmpty()) records=redis.opsForStream().read(Consumer.from(GROUP,CONSUMER),options,StreamOffset.create(OrderController.STREAM,ReadOffset.lastConsumed()));
            if(records==null) return;
            for (var record:records) {
                Map<Object,Object> v=record.getValue();
                OrderEvent event=new OrderEvent(v.get("orderId").toString(),Long.parseLong(v.get("voucherId").toString()),Long.parseLong(v.get("userId").toString()));
                mq.syncSend("dongxiang-orders",event);
                redis.opsForStream().acknowledge(OrderController.STREAM,GROUP,record.getId());
                redis.opsForStream().delete(OrderController.STREAM,record.getId());
            }
        } catch(Exception e) { log.error("订单消息转发失败，稍后重试",e); } finally { lock.unlock(); }
    }
    @Scheduled(fixedDelay=10000) public void closeExpired() {
        for(String id:db.queryForList("SELECT id FROM voucher_order WHERE status='PENDING' AND expires_at<=NOW() ORDER BY expires_at LIMIT 100",String.class)) {
            try { tx.close(id); } catch(Exception e) { log.error("关闭订单失败 {}",id,e); }
        }
    }
    @Scheduled(fixedDelay=5000) public void releaseStock() {
        List<Map<String,Object>> rows=db.queryForList("SELECT order_id,voucher_id FROM stock_release WHERE processed=0 ORDER BY order_id LIMIT 100");
        for(var row:rows) try {
            String id=row.get("order_id").toString(); String voucher=row.get("voucher_id").toString();
            Long result=redis.execute(release,List.of("seckill:{orders}:stock:"+voucher,"seckill:{orders}:released:"+voucher),id);
            if(Long.valueOf(1).equals(result)) db.update("UPDATE stock_release SET processed=1 WHERE order_id=? AND processed=0",id);
        } catch(Exception e) { log.error("库存回补失败",e); }
    }
}

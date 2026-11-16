package com.dongxiang.venue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class VenueService {
    private final VenueMapper mapper; private final StringRedisTemplate redis; private final RedissonClient redisson; private final ObjectMapper json;
    private final ExecutorService executor=Executors.newFixedThreadPool(4);
    public record Cached(Venue venue, long expiresAt) {}
    public VenueService(VenueMapper mapper,StringRedisTemplate redis,RedissonClient redisson,ObjectMapper json) { this.mapper=mapper; this.redis=redis; this.redisson=redisson; this.json=json; }
    public Venue get(long id) {
        if (id<=0) throw new IllegalArgumentException("场馆 ID 无效");
        String key="cache:venue:"+id; String raw=redis.opsForValue().get(key);
        if ("NULL".equals(raw)) return null;
        if (raw!=null) {
            try { Cached cached=json.readValue(raw,Cached.class); if (cached.expiresAt()<System.currentTimeMillis()) rebuildAsync(id); return cached.venue(); }
            catch (Exception e) { redis.delete(key); }
        }
        // A Bloom negative is only a hint: external database changes cannot invalidate it reliably.
        RBloomFilter<Long> bloom=redisson.getBloomFilter("bloom:venues"); bloom.tryInit(1000000,0.01);
        Venue v=mapper.selectById(id);
        if (v==null) { redis.opsForValue().set(key,"NULL",Duration.ofMinutes(2)); return null; }
        bloom.add(id); write(id,v); return v;
    }
    private void rebuildAsync(long id) {
        executor.execute(() -> { RLock lock=redisson.getLock("lock:venue:"+id); if (!lock.tryLock()) return;
            try { String raw=redis.opsForValue().get("cache:venue:"+id); if (raw!=null && !raw.equals("NULL") && json.readValue(raw,Cached.class).expiresAt()>System.currentTimeMillis()) return;
                Venue v=mapper.selectById(id); if(v==null) redis.opsForValue().set("cache:venue:"+id,"NULL",Duration.ofMinutes(2)); else write(id,v);
            } catch (Exception e) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("venue cache rebuild failed: {}",id,e); }
            finally { lock.unlock(); }
        });
    }
    private void write(long id,Venue venue) {
        try { long logical=System.currentTimeMillis()+Duration.ofMinutes(30).toMillis()+ThreadLocalRandom.current().nextLong(Duration.ofMinutes(5).toMillis());
            redis.opsForValue().set("cache:venue:"+id,json.writeValueAsString(new Cached(venue,logical)),Duration.ofHours(2).plusSeconds(ThreadLocalRandom.current().nextInt(300)));
        } catch (Exception e) { throw new IllegalStateException("缓存写入失败",e); }
    }
    @PreDestroy public void shutdown() { executor.shutdown(); }
}

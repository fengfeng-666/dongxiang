package com.dongxiang.venue;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dongxiang.common.Api;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/venues")
public class VenueController {
    private final VenueMapper mapper; private final VenueService service;
    public VenueController(VenueMapper mapper, VenueService service) { this.mapper=mapper; this.service=service; }
    @GetMapping public Api<?> list(@RequestParam(required=false) String city,@RequestParam(required=false) String category,@RequestParam(defaultValue="1") int page) {
        int offset=(Math.max(1,Math.min(page,10000))-1)*20;
        QueryWrapper<Venue> q=new QueryWrapper<>(); if(city!=null&&!city.isBlank()) q.eq("city",city); if(category!=null&&!category.isBlank()) q.eq("category",category);
        q.orderByAsc("id").last("LIMIT 20 OFFSET "+offset); return Api.ok(mapper.selectList(q));
    }
    @GetMapping("/{id}") public Api<?> get(@PathVariable long id) { Venue v=service.get(id); return v==null?Api.error("场馆不存在"):Api.ok(v); }
}

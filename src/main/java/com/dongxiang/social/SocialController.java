package com.dongxiang.social;

import com.dongxiang.auth.CurrentUser;
import com.dongxiang.common.Api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/posts")
public class SocialController {
    private final JdbcTemplate db; public SocialController(JdbcTemplate db) { this.db=db; }
    public record Post(@NotNull Long venueId,@NotBlank @Size(max=120) String title,@NotBlank @Size(max=5000) String content) {}
    public record Comment(@NotBlank @Size(max=1000) String content) {}
    @GetMapping public Api<?> list(@RequestParam(defaultValue="1") int page) { int offset=(Math.max(1,Math.min(page,10000))-1)*20; return Api.ok(db.queryForList("SELECT p.*, u.nickname FROM post p JOIN app_user u ON u.id=p.user_id ORDER BY p.id DESC LIMIT 20 OFFSET ?",offset)); }
    @PostMapping public Api<?> create(@Valid @RequestBody Post p) { if (db.queryForObject("SELECT count(*) FROM venue WHERE id=?",Integer.class,p.venueId())==0) throw new IllegalArgumentException("场馆不存在"); db.update("INSERT INTO post(user_id,venue_id,title,content) VALUES (?,?,?,?)",CurrentUser.require(),p.venueId(),p.title(),p.content()); return Api.ok(null); }
    @PostMapping("/{id}/likes") public Api<?> like(@PathVariable long id) { try { db.update("INSERT INTO post_like(post_id,user_id) VALUES (?,?)",id,CurrentUser.require()); } catch (DuplicateKeyException e) { return Api.ok(Map.of("liked",true)); } return Api.ok(Map.of("liked",true)); }
    @DeleteMapping("/{id}/likes") public Api<?> unlike(@PathVariable long id) { db.update("DELETE FROM post_like WHERE post_id=? AND user_id=?",id,CurrentUser.require()); return Api.ok(Map.of("liked",false)); }
    @GetMapping("/{id}/comments") public Api<?> comments(@PathVariable long id) { return Api.ok(db.queryForList("SELECT c.*,u.nickname FROM post_comment c JOIN app_user u ON u.id=c.user_id WHERE c.post_id=? ORDER BY c.id LIMIT 100",id)); }
    @PostMapping("/{id}/comments") public Api<?> comment(@PathVariable long id,@Valid @RequestBody Comment c) { db.update("INSERT INTO post_comment(post_id,user_id,content) VALUES (?,?,?)",id,CurrentUser.require(),c.content()); return Api.ok(null); }
}

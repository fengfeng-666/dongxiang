package com.dongxiang.order;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

@Service
public class OrderTransactions {
    private final JdbcTemplate db; public OrderTransactions(JdbcTemplate db) { this.db=db; }
    @Transactional public void create(OrderEvent event) {
        Integer existing=db.queryForObject("SELECT count(*) FROM voucher_order WHERE id=?",Integer.class,event.orderId());
        if(existing != null && existing>0) return;
        // Insert first. A duplicate delivery cannot decrement stock twice; the transaction rolls back on failure.
        try { db.update("INSERT INTO voucher_order(id,voucher_id,user_id,status,expires_at) VALUES (?,?,?,'PENDING',DATE_ADD(NOW(), INTERVAL 15 MINUTE))",event.orderId(),event.voucherId(),event.userId()); }
        catch (DuplicateKeyException e) {
            Integer same=db.queryForObject("SELECT count(*) FROM voucher_order WHERE id=? AND voucher_id=? AND user_id=?",Integer.class,event.orderId(),event.voucherId(),event.userId());
            if (same != null && same>0) return;
            throw e;
        }
        if(db.update("UPDATE voucher SET stock=stock-1 WHERE id=? AND stock>0",event.voucherId())!=1) throw new IllegalStateException("数据库库存不足，消息保留以供重试和排查");
    }
    @Transactional public boolean close(String id) {
        int updated=db.update("UPDATE voucher_order SET status='CLOSED',updated_at=NOW() WHERE id=? AND status='PENDING' AND expires_at<=NOW()",id);
        if(updated==0) return false;
        List<Map<String,Object>> rows=db.queryForList("SELECT voucher_id,user_id FROM voucher_order WHERE id=?",id);
        Map<String,Object> row=rows.get(0); db.update("UPDATE voucher SET stock=stock+1 WHERE id=?",row.get("voucher_id"));
        db.update("INSERT INTO stock_release(order_id,voucher_id,user_id) VALUES (?,?,?)",id,row.get("voucher_id"),row.get("user_id"));
        return true;
    }
    @Transactional public boolean pay(String id) { return db.update("UPDATE voucher_order SET status='PAID',updated_at=NOW() WHERE id=? AND status='PENDING' AND expires_at>NOW()",id)==1; }
}

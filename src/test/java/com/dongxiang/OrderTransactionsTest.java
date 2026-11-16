package com.dongxiang;

import com.dongxiang.order.OrderEvent;
import com.dongxiang.order.OrderTransactions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OrderTransactionsTest {
    @Test void duplicateDeliveryDoesNotDeductStockAgain() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(),eq(Integer.class),any())).thenReturn(1);
        new OrderTransactions(db).create(new OrderEvent("order",1,2));
        verify(db,never()).update(anyString(),any(),any(),any());
    }
    @Test void zeroStockFailsSoTransactionCanRollBack() {
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.queryForObject(anyString(),eq(Integer.class),any())).thenReturn(0);
        when(db.update(contains("INSERT INTO voucher_order"),any(),any(),any())).thenReturn(1);
        when(db.update(contains("UPDATE voucher SET stock"),anyLong())).thenReturn(0);
        assertThrows(IllegalStateException.class,()->new OrderTransactions(db).create(new OrderEvent("order",1,2)));
    }
}

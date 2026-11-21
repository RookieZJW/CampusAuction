package com.campus.auction;

import com.campus.auction.common.exception.BusinessException;
import com.campus.auction.payment.PaymentNotify;
import com.campus.auction.service.PaymentService;
import com.campus.auction.vo.PayPrepareVO;
import com.campus.auction.vo.PayStatusVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 方案 B 支付状态机一次性冒烟：造一笔临时待交割订单，跑完主链路后删除。
 * 验证：发起 → 幂等重发 → 确认(回调链路) → 重复确认幂等 → 重复回调幂等 → 卖方查询 → 已支付保护。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentSmokeTest {

    private static final String ORDER_NO = "SMOKE-PAY-20261121";

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long orderId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM t_notification WHERE ref_id IN "
                + "(SELECT id FROM t_order WHERE order_no = ?) AND type = 'PAYMENT'", ORDER_NO);
        jdbcTemplate.update("DELETE FROM t_order WHERE order_no = ?", ORDER_NO);
        jdbcTemplate.update(
                "INSERT INTO t_order (order_no, auction_id, item_id, buyer_id, seller_id, deal_price, "
                        + "status, trade_type, pay_status, create_time, update_time) "
                        + "VALUES (?, 999999, 6, 97, 96, 1500, 'PENDING_TRADING', 'OFFLINE', 'UNPAID', NOW(), NOW())",
                ORDER_NO);
        orderId = jdbcTemplate.queryForObject("SELECT id FROM t_order WHERE order_no = ?", Long.class, ORDER_NO);
    }

    @AfterEach
    void tearDown() {
        if (orderId != null) {
            jdbcTemplate.update("DELETE FROM t_notification WHERE ref_id = ? AND type = 'PAYMENT'", orderId);
            jdbcTemplate.update("DELETE FROM t_order WHERE id = ?", orderId);
        }
    }

    @Test
    void fullPayFlow() {
        PayPrepareVO p1 = paymentService.prepare(orderId, 97L);
        assertEquals("PAYING", p1.getPayStatus());
        assertEquals("MOCK", p1.getProvider());
        assertTrue(p1.getMock());
        assertNotNull(p1.getPayNo());
        assertEquals("15.00", p1.getAmountYuan());

        PayPrepareVO p2 = paymentService.prepare(orderId, 97L);
        assertEquals(p1.getPayNo(), p2.getPayNo(), "重复发起必须幂等返回同一支付单");

        PayStatusVO paid = paymentService.confirm(orderId, 97L);
        assertEquals("PAID", paid.getPayStatus());
        assertNotNull(paid.getTransactionId());
        assertTrue(paid.getTransactionId().startsWith("MOCK"));
        assertNotNull(paid.getPaidAt());
        assertFalse(paid.getPaidAt().isBlank());

        PayStatusVO again = paymentService.confirm(orderId, 97L);
        assertEquals("PAID", again.getPayStatus());
        assertEquals(paid.getTransactionId(), again.getTransactionId(), "重复确认必须幂等");

        PayStatusVO dupNotify = paymentService.handleNotify(PaymentNotify.builder()
                .provider("MOCK")
                .payNo(p1.getPayNo())
                .transactionId("MOCK_DUP_SHOULD_NOT_OVERWRITE")
                .build());
        assertEquals("PAID", dupNotify.getPayStatus());
        assertEquals(paid.getTransactionId(), dupNotify.getTransactionId(), "重复回调不得覆盖交易号");

        PayStatusVO sellerView = paymentService.status(orderId, 96L);
        assertEquals("PAID", sellerView.getPayStatus());

        assertThrows(BusinessException.class, () -> paymentService.close(orderId, 97L),
                "已支付订单不可关闭支付单");

        System.out.println("SMOKE_PAY_FLOW_OK payNo=" + p1.getPayNo()
                + " transactionId=" + paid.getTransactionId() + " paidAt=" + paid.getPaidAt());
    }
}

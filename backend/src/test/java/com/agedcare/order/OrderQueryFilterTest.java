package com.agedcare.order;

import com.agedcare.modules.order.dto.OrderQueryDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OrderQueryFilterTest {
    @Test void emptyAndSerializedNullMeanNoStatusFilter() {
        var query = new OrderQueryDTO();
        for (String value : new String[]{null, "", "  ", "null", " NULL ", "undefined"}) {
            query.setStatus(value);
            assertNull(query.getStatus());
        }
    }
    @Test void explicitStatusAndGroupedDispatchFilterArePreserved() {
        var query = new OrderQueryDTO();
        query.setStatus(" WAIT_VISIT ");
        assertEquals("WAIT_VISIT", query.getStatus());
        query.setStatus("WAIT_DISPATCH,WAIT_ACCEPT,WAIT_MANUAL");
        assertEquals("WAIT_DISPATCH,WAIT_ACCEPT,WAIT_MANUAL", query.getStatus());
    }
}

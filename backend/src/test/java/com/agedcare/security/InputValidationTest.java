package com.agedcare.security;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import com.agedcare.modules.elder.dto.ElderSaveDTO;
import com.agedcare.modules.serviceitem.dto.ServiceItemSaveDTO;
import com.agedcare.modules.order.dto.OrderCreateDTO;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
class InputValidationTest {
    @Test void rejectsInvalidFieldsInsteadOfPassingThemToPersistence() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            var elder=new ElderSaveDTO(); elder.setName("老人"); elder.setAddress("社区"); elder.setAge(200); elder.setLatitude(BigDecimal.ZERO);
            var fields=validator.validate(elder).stream().map(v->v.getPropertyPath().toString()).toList();
            assertTrue(fields.contains("age")); assertTrue(fields.contains("coordinatesPaired"));
            var service=new ServiceItemSaveDTO(); service.setName("测试"); service.setCategory("INJECTED"); service.setPrice(new BigDecimal("-1")); service.setDurationMinutes(0);
            assertEquals(3,validator.validate(service).size());
            var order=new OrderCreateDTO(); order.setElderId(-1L); order.setContactPhone("not-phone");
            assertTrue(validator.validate(order).stream().anyMatch(v->v.getPropertyPath().toString().equals("contactPhone")));
        }
    }
}

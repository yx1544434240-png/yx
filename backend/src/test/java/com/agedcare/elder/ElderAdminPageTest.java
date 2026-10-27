package com.agedcare.elder;

import com.agedcare.modules.elder.controller.ElderProfileController;
import com.agedcare.modules.elder.dto.ElderAdminVO;
import com.agedcare.modules.elder.entity.ElderProfile;
import com.agedcare.modules.elder.service.ElderProfileService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ElderAdminPageTest {
    @Test
    void adminPageReturnsMaskedIdCardOnlyAndKeepsPaging() throws Exception {
        ElderProfileService service = mock(ElderProfileService.class);
        Page<ElderProfile> page = new Page<>(2, 20, 41);
        page.setRecords(List.of(ElderProfile.builder().id(7L).name("钱桂芳").idCard("110101194611152345").phone("13900001111").build()));
        when(service.pageElders(2, 20, null, null)).thenReturn(page);

        Page<ElderAdminVO> result = new ElderProfileController(service).page(2, 20, null, null).getData();

        assertEquals(41, result.getTotal());
        assertEquals(2, result.getCurrent());
        assertEquals("110***********2345", result.getRecords().get(0).getIdCardMasked());
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result);
        assertFalse(json.contains("110101194611152345"), json);
        assertFalse(json.contains("\"idCard\""), json);
    }

    @Test
    void maskHandlesBlankAndShortValues() {
        assertEquals("", ElderAdminVO.maskIdCard(null));
        assertEquals("", ElderAdminVO.maskIdCard(" "));
        assertEquals("*****", ElderAdminVO.maskIdCard("12345"));
        assertEquals("110********1234", ElderAdminVO.maskIdCard("110101200001234"));
    }
}

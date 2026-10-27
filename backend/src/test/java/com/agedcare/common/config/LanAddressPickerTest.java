package com.agedcare.common.config;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class LanAddressPickerTest {

    @Test
    void prefersWifiOverVpnAndHyperV() {
        String ip = LanAddressPicker.pick(List.of(
                new LanAddressPicker.Nic("jumengcloud", "198.18.0.1"),
                new LanAddressPicker.Nic("vEthernet (Default Switch)", "172.23.96.1"),
                new LanAddressPicker.Nic("vEthernet (WSL (Hyper-V firewall))", "172.20.144.1"),
                new LanAddressPicker.Nic("WLAN", "10.146.147.237"),
                new LanAddressPicker.Nic("Loopback Pseudo-Interface 1", "127.0.0.1")
        ));
        Assertions.assertEquals("10.146.147.237", ip);
    }

    @Test
    void returnsNullWhenNoUsableLanAddressExists() {
        String ip = LanAddressPicker.pick(List.of(
                new LanAddressPicker.Nic("jumengcloud", "198.18.0.1")
        ));
        Assertions.assertNull(ip);
    }

    @Test
    void buildsUrlFromPortAndContextPath() {
        Assertions.assertEquals("http://192.168.0.8:9090/api", LanAddressPicker.buildLanBaseUrl("192.168.0.8", 9090, "api"));
        Assertions.assertEquals("http://10.0.0.9:8080/api", LanAddressPicker.buildLanBaseUrl("10.0.0.9", 8080, "/api/"));
    }

    @Test
    void rendersAnUncommittedLocalConfig() {
        String file = LanAddressPicker.renderLocalConfig("http://192.168.1.9:8080/api");
        Assertions.assertTrue(file.contains("module.exports = { LAN_BASE_URL: 'http://192.168.1.9:8080/api' };"));
    }

    @Test
    void patchesOnlyTheLanConstant() {
        String source = "const SIMULATOR_BASE_URL = 'http://localhost:8080/api';\nconst LAN_BASE_URL = 'http://10.0.0.1:8080/api';\n";
        String next = LanAddressPicker.patchLanBaseUrl(source, "http://192.168.1.9:8080/api");
        Assertions.assertTrue(next.contains("const LAN_BASE_URL = 'http://192.168.1.9:8080/api';"));
        Assertions.assertTrue(next.contains("const SIMULATOR_BASE_URL = 'http://localhost:8080/api';"));
    }
}

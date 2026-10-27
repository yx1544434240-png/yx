package com.agedcare.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.lan-sync", name = "enabled", havingValue = "true")
public class LanAddressSyncRunner implements ApplicationRunner {

    @Value("${server.port:8080}")
    private int port;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Override
    public void run(ApplicationArguments args) {
        Path root = resolveRepoRoot();
        if (root == null) {
            log.debug("lan-sync skipped because the miniprogram project was not found");
            return;
        }
        Path script = root.resolve("miniprogram/scripts/sync-lan-ip.cjs");
        Path localConfig = root.resolve("miniprogram/config/local.js");
        if (Files.isRegularFile(script) && runNode(script)) {
            return;
        }
        writeWithJava(localConfig);
    }

    private boolean runNode(Path script) {
        try {
            Process process = new ProcessBuilder(
                    "node",
                    script.toAbsolutePath().toString(),
                    "--port", String.valueOf(port),
                    "--context-path", contextPath
            ).redirectErrorStream(true).start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (!finished) {
                process.destroyForcibly();
                log.warn("lan-sync node timed out");
                return false;
            }
            if (process.exitValue() == 0) {
                log.info("真机调试地址已同步，请重新编译小程序后再扫码。{}", output);
                return true;
            }
            log.warn("lan-sync node exited {}: {}", process.exitValue(), output);
            return false;
        } catch (Exception ex) {
            log.debug("lan-sync node unavailable: {}", ex.getMessage());
            return false;
        }
    }

    private void writeWithJava(Path localConfig) {
        try {
            String ip = LanAddressPicker.fromSystem();
            if (ip == null) {
                log.warn("lan-sync did not find a usable LAN IPv4 address");
                return;
            }
            String url = LanAddressPicker.buildLanBaseUrl(ip, port, contextPath);
            String next = LanAddressPicker.renderLocalConfig(url);
            String source = Files.isRegularFile(localConfig) ? Files.readString(localConfig) : "";
            if (!source.equals(next)) {
                Files.createDirectories(localConfig.getParent());
                Files.writeString(localConfig, next);
            }
            log.info("真机调试地址已同步为 {} ，请重新编译小程序后再扫码", url);
        } catch (Exception ex) {
            log.warn("lan-sync fallback failed: {}", ex.getMessage());
        }
    }

    static Path resolveRepoRoot() {
        Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (int i = 0; i < 4 && dir != null; i++) {
            if (Files.isRegularFile(dir.resolve("miniprogram/app.js"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return null;
    }
}

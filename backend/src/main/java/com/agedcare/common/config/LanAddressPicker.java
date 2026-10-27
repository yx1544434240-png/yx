package com.agedcare.common.config;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LanAddressPicker {

    private static final Pattern SKIP_ADAPTER = Pattern.compile(
            "vEthernet|WSL|Hyper-V|Loopback|VirtualBox|VMware|docker|jumeng|Clash|TAP-Windows|VPN|ZeroTier|Bluetooth|Pseudo-Interface",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFER_ADAPTER = Pattern.compile(
            "Wi-?Fi|WLAN|无线|以太网|Ethernet",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LAN_ASSIGNMENT = Pattern.compile("const LAN_BASE_URL = 'http://[^']+'");

    public record Nic(String name, String address) {
    }

    private record Scored(String ip, int rank, String name) {
    }

    private LanAddressPicker() {
    }

    public static boolean isLanIpv4(String ip) {
        if (ip == null) {
            return false;
        }
        String[] raw = ip.split("\\.");
        if (raw.length != 4) {
            return false;
        }
        int[] parts = new int[4];
        try {
            for (int i = 0; i < 4; i++) {
                parts[i] = Integer.parseInt(raw[i]);
                if (parts[i] < 0 || parts[i] > 255) {
                    return false;
                }
            }
        } catch (NumberFormatException ex) {
            return false;
        }
        if (parts[0] == 127 || (parts[0] == 169 && parts[1] == 254)) {
            return false;
        }
        if (parts[0] == 198 && (parts[1] == 18 || parts[1] == 19)) {
            return false;
        }
        if (parts[0] == 10) {
            return true;
        }
        if (parts[0] == 192 && parts[1] == 168) {
            return true;
        }
        return parts[0] == 172 && parts[1] >= 16 && parts[1] <= 31;
    }

    public static String pick(List<Nic> nics) {
        List<Scored> scored = new ArrayList<>();
        for (Nic nic : nics) {
            if (nic == null || nic.name() == null || nic.address() == null) {
                continue;
            }
            if (SKIP_ADAPTER.matcher(nic.name()).find() || !isLanIpv4(nic.address())) {
                continue;
            }
            int prefer = PREFER_ADAPTER.matcher(nic.name()).find() ? 0 : 1;
            int privateClass = nic.address().startsWith("10.") || nic.address().startsWith("192.168.") ? 0 : 1;
            scored.add(new Scored(nic.address(), prefer * 10 + privateClass, nic.name()));
        }
        scored.sort(Comparator.comparingInt(Scored::rank).thenComparing(Scored::name));
        return scored.isEmpty() ? null : scored.get(0).ip();
    }

    public static String fromSystem() throws SocketException {
        List<Nic> nics = new ArrayList<>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) {
            return null;
        }
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            String display = networkInterface.getDisplayName() == null ? "" : networkInterface.getDisplayName();
            String name = networkInterface.getName() == null ? "" : networkInterface.getName();
            String label = (display + " " + name).trim();
            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    nics.add(new Nic(label, address.getHostAddress()));
                }
            }
        }
        return pick(nics);
    }

    public static String buildLanBaseUrl(String ip, int port, String contextPath) {
        String context = contextPath == null || contextPath.isBlank() ? "/api" : contextPath;
        if (!context.startsWith("/")) {
            context = "/" + context;
        }
        context = context.replaceAll("/+$", "");
        return "http://" + ip + ":" + port + context;
    }

    /** 生成未提交的 miniprogram/config/local.js，仅开发版读取，体验版/正式版忽略。 */
    public static String renderLocalConfig(String url) {
        return "// 本文件由 lan-sync 自动生成，已加入 .gitignore，请勿提交。仅开发版(develop)读取。\n"
                + "module.exports = { LAN_BASE_URL: '" + url + "' };\n";
    }

    public static String patchLanBaseUrl(String source, String url) {
        Matcher matcher = LAN_ASSIGNMENT.matcher(source);
        if (!matcher.find()) {
            throw new IllegalStateException("LAN_BASE_URL assignment was not found");
        }
        return matcher.replaceFirst("const LAN_BASE_URL = '" + Matcher.quoteReplacement(url) + "'");
    }
}

package jconnect.network;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceRegistry {
    // Maps IP address to Device Metadata
    private static final Map<String, Long> activeDevices = new ConcurrentHashMap<>();

    public static void updateDevice(String ip) {
        activeDevices.put(ip, System.currentTimeMillis());
    }

    public static List<String> getOnlineDevices() {
        long now = System.currentTimeMillis();
        // Remove devices that haven't broadcasted in 10 seconds
        activeDevices.entrySet().removeIf(entry -> (now - entry.getValue() > 10000));
        return new ArrayList<>(activeDevices.keySet());
    }
}
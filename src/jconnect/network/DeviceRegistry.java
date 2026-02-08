package jconnect.network;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceRegistry {
    private static final Map<String, Long> activeDevices = new ConcurrentHashMap<>();

    public static void updateDevice(String ip) {
        activeDevices.put(ip, System.currentTimeMillis());
    }

    public static List<String> getOnlineDevices() {
        long now = System.currentTimeMillis();
       
        activeDevices.entrySet().removeIf(entry -> (now - entry.getValue() > 8000));
        return new ArrayList<>(activeDevices.keySet());
    }
}
package jconnect.network;

public interface ConnectionObserver {
    void onMessage(String deviceIp, String message);
    void onStatusChange(String deviceIp, boolean isOnline);
}
package jconnect.ui;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatMessage {
    private final String content;
    private final boolean isMe;
    private final boolean isFile;
    private final String timestamp;

    public ChatMessage(String content, boolean isMe, boolean isFile) {
        this.content = content;
        this.isMe = isMe;
        this.isFile = isFile;
        this.timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public String getContent() { return content; }
    public boolean isMe() { return isMe; }
    public boolean isFile() { return isFile; }
    public String getTimestamp() { return timestamp; }
}
package common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String LOGIN             = "LOGIN";
    public static final String LOGIN_SUCCESS     = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL        = "LOGIN_FAIL";
    public static final String REGISTER          = "REGISTER";
    public static final String REGISTER_SUCCESS  = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL     = "REGISTER_FAIL";
    public static final String LOGOUT            = "LOGOUT";
    public static final String CHAT              = "CHAT";
    public static final String USER_LIST         = "USER_LIST";
    public static final String HISTORY_REQ       = "HISTORY_REQ";
    public static final String HISTORY_RESP      = "HISTORY_RESP";

    private String type;
    private String from;
    private String to;
    private String content;
    private long   timestamp;

    public Message(String type, String from, String to, String content) {
        this.type      = type;
        this.from      = from;
        this.to        = to;
        this.content   = content;
        this.timestamp = System.currentTimeMillis();
    }

    // 允许手动设置时间戳（从数据库恢复历史消息时使用）
    public Message(String type, String from, String to, String content, long timestamp) {
        this(type, from, to, content);
        this.timestamp = timestamp;
    }

    public String getType()      { return type; }
    public String getFrom()      { return from; }
    public String getTo()        { return to; }
    public String getContent()   { return content; }
    public long   getTimestamp() { return timestamp; }

    public String getFormattedTime() {
        return new SimpleDateFormat("HH:mm").format(new Date(timestamp));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s -> %s: %s", type, from, to, content);
    }
}

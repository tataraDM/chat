package common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final String LOGIN            = "LOGIN";
    public static final String LOGIN_SUCCESS    = "LOGIN_SUCCESS";
    public static final String LOGIN_FAIL       = "LOGIN_FAIL";
    public static final String REGISTER         = "REGISTER";
    public static final String REGISTER_SUCCESS = "REGISTER_SUCCESS";
    public static final String REGISTER_FAIL    = "REGISTER_FAIL";
    public static final String LOGOUT           = "LOGOUT";
    public static final String CHAT             = "CHAT";
    public static final String USER_LIST        = "USER_LIST";        // 在线用户（逗号分隔）
    public static final String CONTACTS         = "CONTACTS";         // 我的联系人列表
    public static final String HISTORY_REQ      = "HISTORY_REQ";
    public static final String HISTORY_RESP     = "HISTORY_RESP";
    public static final String FRIEND_REQ       = "FRIEND_REQ";       // 发送好友请求
    public static final String FRIEND_ACCEPT    = "FRIEND_ACCEPT";    // 接受好友请求
    public static final String FRIEND_REJECT    = "FRIEND_REJECT";    // 拒绝好友请求
    public static final String ALL_USERS_REQ    = "ALL_USERS_REQ";    // 查询所有注册用户
    public static final String ALL_USERS_RESP   = "ALL_USERS_RESP";   // 所有注册用户列表
    public static final String PENDING_REQUESTS = "PENDING_REQUESTS"; // 待处理好友申请列表
    public static final String FRIEND_DELETE    = "FRIEND_DELETE";     // 删除好友

    // === 群聊相关 ===
    public static final String GROUP_CREATE     = "GROUP_CREATE";     // 创建群聊 content=群名,成员1,成员2...
    public static final String GROUP_CREATE_OK  = "GROUP_CREATE_OK";  // 创建成功 content=groupId
    public static final String GROUP_CHAT       = "GROUP_CHAT";       // 群消息 to=groupId
    public static final String GROUP_LIST       = "GROUP_LIST";       // 群列表 content=id:name,id:name...
    public static final String GROUP_HISTORY_REQ  = "GROUP_HISTORY_REQ";  // 请求群聊历史
    public static final String GROUP_HISTORY_RESP = "GROUP_HISTORY_RESP"; // 群聊历史响应

    // === 搜索相关 ===
    public static final String SEARCH_REQ       = "SEARCH_REQ";      // 搜索私聊记录 content=peer|keyword
    public static final String SEARCH_RESP      = "SEARCH_RESP";     // 私聊搜索结果
    public static final String GROUP_SEARCH_REQ  = "GROUP_SEARCH_REQ";  // 搜索群聊记录 content=groupId|keyword
    public static final String GROUP_SEARCH_RESP = "GROUP_SEARCH_RESP"; // 群聊搜索结果

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

    public String getFormattedDateTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    @Override
    public String toString() {
        return String.format("[%s] %s -> %s: %s", type, from, to, content);
    }
}

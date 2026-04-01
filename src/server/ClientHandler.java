package server;

import common.Message;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private ObjectOutputStream out;
    private String username;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() { return username; }

    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            System.err.println("[Handler] 发送失败到 " + username + ": " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            while (true) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }
        } catch (EOFException | java.net.SocketException e) {
            // 正常断开
        } catch (Exception e) {
            System.err.println("[Handler] 异常: " + e.getMessage());
        } finally {
            if (username != null) {
                server.removeUser(username);
                System.out.println("[Server] 用户下线: " + username);
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(Message msg) {
        DatabaseManager db = DatabaseManager.getInstance();
        switch (msg.getType()) {

            case Message.REGISTER: {
                String uname = msg.getFrom();
                if (uname == null || uname.isBlank()) {
                    send(new Message(Message.REGISTER_FAIL, "server", uname, "用户名不能为空"));
                    return;
                }
                if (uname.length() > 20) {
                    send(new Message(Message.REGISTER_FAIL, "server", uname, "用户名不能超过20个字符"));
                    return;
                }
                String pwd = msg.getContent();
                if (pwd == null || pwd.length() < 3) {
                    send(new Message(Message.REGISTER_FAIL, "server", uname, "密码不能少于3个字符"));
                    return;
                }
                if (db.register(uname, pwd)) {
                    System.out.println("[Server] 新用户注册: " + uname);
                    send(new Message(Message.REGISTER_SUCCESS, "server", uname, "注册成功"));
                } else {
                    send(new Message(Message.REGISTER_FAIL, "server", uname, "用户名已存在"));
                }
                break;
            }

            case Message.LOGIN: {
                String uname = msg.getFrom();
                String pwd   = msg.getContent();
                if (!db.userExists(uname)) {
                    send(new Message(Message.LOGIN_FAIL, "server", uname, "用户不存在，请先注册"));
                    return;
                }
                if (!db.validate(uname, pwd)) {
                    send(new Message(Message.LOGIN_FAIL, "server", uname, "密码错误"));
                    return;
                }
                if (server.getUser(uname) != null) {
                    send(new Message(Message.LOGIN_FAIL, "server", uname, "该账号已在其他地方登录"));
                    return;
                }
                username = uname;
                server.addUser(username, this);
                System.out.println("[Server] 用户上线: " + username);
                send(new Message(Message.LOGIN_SUCCESS, "server", username, "登录成功"));
                // 发送联系人列表
                sendContacts(db);
                // 发送群列表
                sendGroupList(db);
                // 推送待处理的好友申请
                sendPendingRequests(db);
                // 广播在线状态
                server.broadcastUserList();
                break;
            }

            case Message.CHAT: {
                db.saveMessage(msg);
                ClientHandler target = server.getUser(msg.getTo());
                if (target != null) target.send(msg);
                break;
            }

            case Message.HISTORY_REQ: {
                String peer = msg.getContent();
                List<Message> history = db.getHistory(msg.getFrom(), peer);
                for (Message h : history) {
                    send(new Message(Message.HISTORY_RESP,
                            h.getFrom(), h.getTo(), h.getContent(), h.getTimestamp()));
                }
                send(new Message(Message.HISTORY_RESP, "__END__", peer, ""));
                break;
            }

            case Message.ALL_USERS_REQ: {
                List<String> all = db.getAllUsers(msg.getFrom());
                send(new Message(Message.ALL_USERS_RESP, "server", msg.getFrom(),
                        String.join(",", all)));
                break;
            }

            case Message.FRIEND_REQ: {
                String from = msg.getFrom();
                String to   = msg.getTo();
                if (db.areFriends(from, to)) {
                    send(new Message(Message.FRIEND_REJECT, "server", from, "你们已经是联系人了"));
                    return;
                }
                if (db.hasPendingRequest(from, to)) {
                    send(new Message(Message.FRIEND_REJECT, "server", from, "请求已发送，等待对方确认"));
                    return;
                }
                db.sendFriendRequest(from, to);
                // 如果对方在线，实时推送请求
                ClientHandler targetHandler = server.getUser(to);
                if (targetHandler != null) {
                    targetHandler.send(new Message(Message.FRIEND_REQ, from, to, ""));
                }
                send(new Message(Message.FRIEND_ACCEPT, "server", from, "请求已发送"));
                break;
            }

            case Message.FRIEND_ACCEPT: {
                String acceptor  = msg.getFrom();
                String requester = msg.getTo();
                boolean accept   = "accept".equals(msg.getContent());
                if (accept) {
                    db.acceptFriendRequest(requester, acceptor);
                    sendContacts(db);
                    ClientHandler requesterHandler = server.getUser(requester);
                    if (requesterHandler != null) {
                        requesterHandler.sendContacts(db);
                        requesterHandler.send(new Message(Message.FRIEND_ACCEPT,
                                acceptor, requester, "accept"));
                    }
                } else {
                    db.rejectFriendRequest(requester, acceptor);
                    ClientHandler requesterHandler = server.getUser(requester);
                    if (requesterHandler != null) {
                        requesterHandler.send(new Message(Message.FRIEND_REJECT,
                                acceptor, requester, acceptor + " 拒绝了你的好友请求"));
                    }
                }
                break;
            }

            case Message.FRIEND_DELETE: {
                String deleter = msg.getFrom();
                String target  = msg.getTo();
                db.deleteFriend(deleter, target);
                // 刷新双方联系人列表
                sendContacts(db);
                ClientHandler targetHandler = server.getUser(target);
                if (targetHandler != null) {
                    targetHandler.sendContacts(db);
                }
                send(new Message(Message.FRIEND_DELETE, "server", deleter, "已删除好友 " + target));
                break;
            }

            // === 群聊 ===
            case Message.GROUP_CREATE: {
                // content = 群名,成员1,成员2,...
                String[] parts = msg.getContent().split(",");
                if (parts.length < 2) {
                    send(new Message(Message.FRIEND_REJECT, "server", msg.getFrom(), "至少选择一个群成员"));
                    return;
                }
                String groupName = parts[0];
                List<String> members = new ArrayList<>();
                for (int i = 1; i < parts.length; i++) members.add(parts[i].trim());
                int groupId = db.createGroup(groupName, msg.getFrom(), members);
                if (groupId > 0) {
                    send(new Message(Message.GROUP_CREATE_OK, "server", msg.getFrom(),
                            String.valueOf(groupId)));
                    // 给所有成员刷新群列表
                    sendGroupList(db);
                    for (String m : members) {
                        ClientHandler mh = server.getUser(m);
                        if (mh != null) mh.sendGroupList(db);
                    }
                }
                break;
            }

            case Message.GROUP_CHAT: {
                int groupId = Integer.parseInt(msg.getTo());
                db.saveGroupMessage(groupId, msg);
                // 广播给群内所有在线成员（除发送者自己）
                List<String> members = db.getGroupMembers(groupId);
                for (String m : members) {
                    if (!m.equals(msg.getFrom())) {
                        ClientHandler mh = server.getUser(m);
                        if (mh != null) mh.send(msg);
                    }
                }
                break;
            }

            case Message.GROUP_HISTORY_REQ: {
                int groupId = Integer.parseInt(msg.getContent());
                List<Message> history = db.getGroupHistory(groupId);
                for (Message h : history) {
                    send(new Message(Message.GROUP_HISTORY_RESP,
                            h.getFrom(), h.getTo(), h.getContent(), h.getTimestamp()));
                }
                send(new Message(Message.GROUP_HISTORY_RESP, "__END__",
                        String.valueOf(groupId), ""));
                break;
            }

            // === 搜索 ===
            case Message.SEARCH_REQ: {
                // content = peer|keyword
                String[] sp = msg.getContent().split("\\|", 2);
                if (sp.length < 2) break;
                List<Message> results = db.searchMessages(msg.getFrom(), sp[0], sp[1]);
                for (Message r : results) {
                    send(new Message(Message.SEARCH_RESP,
                            r.getFrom(), r.getTo(), r.getContent(), r.getTimestamp()));
                }
                send(new Message(Message.SEARCH_RESP, "__END__", sp[0], ""));
                break;
            }

            // === 群聊搜索 ===
            case Message.GROUP_SEARCH_REQ: {
                // content = groupId|keyword
                String[] sp = msg.getContent().split("\\|", 2);
                if (sp.length < 2) break;
                int groupId = Integer.parseInt(sp[0]);
                List<Message> results = db.searchGroupMessages(groupId, sp[1]);
                for (Message r : results) {
                    send(new Message(Message.GROUP_SEARCH_RESP,
                            r.getFrom(), r.getTo(), r.getContent(), r.getTimestamp()));
                }
                send(new Message(Message.GROUP_SEARCH_RESP, "__END__", sp[0], ""));
                break;
            }

            case Message.LOGOUT:
                if (username != null) {
                    server.removeUser(username);
                    server.broadcastUserList();
                }
                break;

            default:
                break;
        }
    }

    /** 向本客户端推送最新联系人列表 */
    void sendContacts(DatabaseManager db) {
        List<String> contacts = db.getContacts(username);
        send(new Message(Message.CONTACTS, "server", username,
                String.join(",", contacts)));
    }

    /** 向本客户端推送群列表 */
    void sendGroupList(DatabaseManager db) {
        List<String> groups = db.getUserGroups(username);
        send(new Message(Message.GROUP_LIST, "server", username,
                String.join(",", groups)));
    }

    /** 向本客户端推送待处理的好友申请列表 */
    void sendPendingRequests(DatabaseManager db) {
        List<String> pending = db.getPendingRequestsTo(username);
        if (!pending.isEmpty()) {
            send(new Message(Message.PENDING_REQUESTS, "server", username,
                    String.join(",", pending)));
        }
    }
}

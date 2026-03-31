package server;

import common.Message;
import java.io.*;
import java.net.Socket;
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
            // 客户端正常断开
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
                String pwd   = msg.getContent();
                if (uname == null || uname.isBlank()) {
                    send(new Message(Message.REGISTER_FAIL, "server", uname, "用户名不能为空"));
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
                    send(new Message(Message.HISTORY_RESP, h.getFrom(), h.getTo(), h.getContent(), h.getTimestamp()));
                }
                send(new Message(Message.HISTORY_RESP, "__END__", peer, ""));
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
}

package client;

import common.Message;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ChatClient {
    private static final String HOST = "127.0.0.1";
    private static final int    PORT = 9090;

    private Socket socket;
    private ObjectOutputStream out;
    private String username;
    private MessageListener listener;

    /** 在 listener 设置前收到的普通消息缓冲，避免丢失 */
    private final List<Message> messageBuffer = new ArrayList<>();

    /** 用于阻塞等待登录/注册结果 */
    private final LinkedBlockingQueue<Message> authQueue = new LinkedBlockingQueue<>();

    public interface MessageListener {
        void onMessage(Message msg);
        void onDisconnected();
    }

    public ChatClient(String username) {
        this.username = username;
    }

    public synchronized void setListener(MessageListener listener) {
        this.listener = listener;
        // 将缓冲期间收到的消息全部补发给 listener
        for (Message m : messageBuffer) listener.onMessage(m);
        messageBuffer.clear();
    }

    /** 连接并登录，返回 null=成功，否则为错误信息 */
    public String connect(String password) {
        try {
            socket = new Socket(HOST, PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            new Thread(this::receiveLoop, "recv-" + username).start();
            send(new Message(Message.LOGIN, username, "server", password));
            Message result = authQueue.poll(6, TimeUnit.SECONDS);
            if (result == null) return "连接超时，请检查服务器是否启动";
            if (Message.LOGIN_SUCCESS.equals(result.getType())) return null;
            return result.getContent();
        } catch (InterruptedException e) {
            return "连接被中断";
        } catch (IOException e) {
            return "无法连接服务器：" + e.getMessage();
        }
    }

    /** 注册账号，返回 null=成功，否则为错误信息 */
    public String register(String password) {
        try {
            socket = new Socket(HOST, PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            new Thread(this::receiveLoop, "recv-reg-" + username).start();
            send(new Message(Message.REGISTER, username, "server", password));
            Message result = authQueue.poll(6, TimeUnit.SECONDS);
            if (result == null) return "连接超时";
            if (Message.REGISTER_SUCCESS.equals(result.getType())) return null;
            return result.getContent();
        } catch (IOException e) {
            return "无法连接服务器：" + e.getMessage();
        } catch (InterruptedException e) {
            return "连接被中断";
        } finally {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        }
    }

    public synchronized void send(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            System.err.println("[Client] 发送失败: " + e.getMessage());
        }
    }

    public void requestHistory(String peerUsername) {
        send(new Message(Message.HISTORY_REQ, username, "server", peerUsername));
    }

    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                send(new Message(Message.LOGOUT, username, "server", ""));
                socket.close();
            }
        } catch (IOException ignored) {}
    }

    public String getUsername() { return username; }

    private void receiveLoop() {
        try {
            ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
            while (true) {
                Message msg = (Message) in.readObject();
                String t = msg.getType();
                // 登录/注册结果走 authQueue
                if (t.equals(Message.LOGIN_SUCCESS) || t.equals(Message.LOGIN_FAIL)
                        || t.equals(Message.REGISTER_SUCCESS) || t.equals(Message.REGISTER_FAIL)) {
                    authQueue.offer(msg);
                } else {
                    // 普通消息：listener 已设置则直接转发，否则缓冲
                    synchronized (this) {
                        if (listener != null) {
                            listener.onMessage(msg);
                        } else {
                            messageBuffer.add(msg);
                        }
                    }
                }
            }
        } catch (EOFException | java.net.SocketException e) {
            synchronized (this) {
                if (listener != null) listener.onDisconnected();
            }
        } catch (Exception e) {
            System.err.println("[Client] 接收异常: " + e.getMessage());
            synchronized (this) {
                if (listener != null) listener.onDisconnected();
            }
        }
    }
}

package server;

import common.Message;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {
    public static final int PORT = 9090;

    private final Map<String, ClientHandler> onlineUsers = new ConcurrentHashMap<>();

    public void start() throws IOException {
        // 初始化数据库
        DatabaseManager.getInstance().init();

        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[Server] 聊天服务器已启动，监听端口 " + PORT);
        while (true) {
            Socket socket = serverSocket.accept();
            System.out.println("[Server] 新连接: " + socket.getRemoteSocketAddress());
            new Thread(new ClientHandler(socket, this)).start();
        }
    }

    public void addUser(String username, ClientHandler handler) {
        onlineUsers.put(username, handler);
    }

    public void removeUser(String username) {
        onlineUsers.remove(username);
        broadcastUserList();
    }

    public ClientHandler getUser(String username) {
        return onlineUsers.get(username);
    }

    public void broadcastUserList() {
        List<String> names = new ArrayList<>(onlineUsers.keySet());
        String content = String.join(",", names);
        Message msg = new Message(Message.USER_LIST, "server", "all", content);
        for (ClientHandler h : onlineUsers.values()) h.send(msg);
    }

    public static void main(String[] args) throws IOException {
        new ChatServer().start();
    }
}

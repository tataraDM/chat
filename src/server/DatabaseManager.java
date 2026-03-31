package server;

import common.Message;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据库操作：用户注册/验证 + 消息存储
 *
 * ====== 配置区 ======
 * 修改下面三个常量以匹配你的 MySQL 环境
 */
public class DatabaseManager {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/chatte" +
            "?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "root";   // ← 改成你的 MySQL 密码
    // ====================

    private static DatabaseManager instance;

    private DatabaseManager() {}

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private Connection getConn() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
    }

    /** 启动时建表（库需已存在） */
    public void init() {
        String createUsers = "CREATE TABLE IF NOT EXISTS users (" +
                "username VARCHAR(50) PRIMARY KEY," +
                "password VARCHAR(64) NOT NULL," +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        String createMessages = "CREATE TABLE IF NOT EXISTS messages (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                "from_user VARCHAR(50) NOT NULL," +
                "to_user   VARCHAR(50) NOT NULL," +
                "content   TEXT NOT NULL," +
                "send_time BIGINT NOT NULL," +
                "INDEX idx_pair(from_user, to_user)," +
                "INDEX idx_time(send_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";

        try (Connection conn = getConn();
             Statement st = conn.createStatement()) {
            st.execute(createUsers);
            st.execute(createMessages);
            System.out.println("[DB] 数据表初始化完成");
        } catch (SQLException e) {
            System.err.println("[DB] 初始化失败: " + e.getMessage());
            throw new RuntimeException("数据库连接失败，请检查 DatabaseManager 中的配置", e);
        }
    }

    // ===== 用户管理 =====

    /** 注册用户，用户名已存在返回 false */
    public boolean register(String username, String password) {
        String sql = "INSERT IGNORE INTO users(username, password) VALUES(?, ?)";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash(password));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] 注册失败: " + e.getMessage());
            return false;
        }
    }

    /** 验证用户名密码，匹配返回 true */
    public boolean validate(String username, String password) {
        String sql = "SELECT 1 FROM users WHERE username=? AND password=?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash(password));
            return ps.executeQuery().next();
        } catch (SQLException e) {
            System.err.println("[DB] 验证失败: " + e.getMessage());
            return false;
        }
    }

    /** 用户名是否存在 */
    public boolean userExists(String username) {
        String sql = "SELECT 1 FROM users WHERE username=?";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException e) {
            return false;
        }
    }

    // ===== 消息存储 =====

    /** 保存一条聊天消息 */
    public void saveMessage(Message msg) {
        String sql = "INSERT INTO messages(from_user, to_user, content, send_time) VALUES(?,?,?,?)";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, msg.getFrom());
            ps.setString(2, msg.getTo());
            ps.setString(3, msg.getContent());
            ps.setLong(4, msg.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] 保存消息失败: " + e.getMessage());
        }
    }

    /** 查询两个用户之间的历史消息（按时间升序） */
    public List<Message> getHistory(String user1, String user2) {
        List<Message> list = new ArrayList<>();
        String sql = "SELECT from_user, to_user, content, send_time FROM messages " +
                "WHERE (from_user=? AND to_user=?) OR (from_user=? AND to_user=?) " +
                "ORDER BY send_time ASC";
        try (Connection conn = getConn();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, user1); ps.setString(2, user2);
            ps.setString(3, user2); ps.setString(4, user1);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Message(
                        Message.CHAT,
                        rs.getString("from_user"),
                        rs.getString("to_user"),
                        rs.getString("content"),
                        rs.getLong("send_time")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[DB] 查询历史失败: " + e.getMessage());
        }
        return list;
    }

    // ===== 工具 =====

    private static String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}

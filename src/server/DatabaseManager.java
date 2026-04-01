package server;

import common.Message;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ====== 配置区 ======
 * 修改下面常量以匹配你的 MySQL 环境
 */
public class DatabaseManager {

    private static final String DB_URL  = "jdbc:mysql://localhost:3306/chatte" +
            "?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8&allowPublicKeyRetrieval=true";
    private static final String DB_USER = "root";
    private static final String DB_PASS = "123456";  // ← 改成你的 MySQL 密码
    // ====================

    private static DatabaseManager instance;
    /** 复用连接，避免每次操作都新建 */
    private Connection sharedConn;

    private DatabaseManager() {}
    public static synchronized DatabaseManager getInstance() {
        if (instance == null) instance = new DatabaseManager();
        return instance;
    }

    private synchronized Connection getConn() throws SQLException {
        if (sharedConn == null || sharedConn.isClosed()) {
            sharedConn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS);
        }
        return sharedConn;
    }

    public void init() {
        try (Statement st = getConn().createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "username VARCHAR(50) PRIMARY KEY," +
                    "password VARCHAR(64) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.execute("CREATE TABLE IF NOT EXISTS messages (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "from_user VARCHAR(50) NOT NULL," +
                    "to_user   VARCHAR(50) NOT NULL," +
                    "content   TEXT NOT NULL," +
                    "send_time BIGINT NOT NULL," +
                    "INDEX idx_pair(from_user, to_user)," +
                    "INDEX idx_time(send_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.execute("CREATE TABLE IF NOT EXISTS friend_requests (" +
                    "from_user VARCHAR(50) NOT NULL," +
                    "to_user   VARCHAR(50) NOT NULL," +
                    "status    VARCHAR(10) NOT NULL DEFAULT 'pending'," +
                    "PRIMARY KEY (from_user, to_user)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            // === 群聊表 ===
            st.execute("CREATE TABLE IF NOT EXISTS chat_groups (" +
                    "group_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "group_name VARCHAR(100) NOT NULL," +
                    "creator VARCHAR(50) NOT NULL," +
                    "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.execute("CREATE TABLE IF NOT EXISTS group_members (" +
                    "group_id INT NOT NULL," +
                    "username VARCHAR(50) NOT NULL," +
                    "PRIMARY KEY (group_id, username)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            st.execute("CREATE TABLE IF NOT EXISTS group_messages (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY," +
                    "group_id  INT NOT NULL," +
                    "from_user VARCHAR(50) NOT NULL," +
                    "content   TEXT NOT NULL," +
                    "send_time BIGINT NOT NULL," +
                    "INDEX idx_group(group_id)," +
                    "INDEX idx_time(send_time)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            System.out.println("[DB] 数据表初始化完成");
        } catch (SQLException e) {
            System.err.println("[DB] 初始化失败: " + e.getMessage());
            throw new RuntimeException("数据库连接失败，请检查 DatabaseManager 中的配置", e);
        }
    }

    // ===== 用户 =====

    public boolean register(String username, String password) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "INSERT IGNORE INTO users(username,password) VALUES(?,?)");
            ps.setString(1, username);
            ps.setString(2, hash(password));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] 注册失败: " + e.getMessage());
            return false;
        }
    }

    public boolean validate(String username, String password) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT 1 FROM users WHERE username=? AND password=?");
            ps.setString(1, username);
            ps.setString(2, hash(password));
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    public boolean userExists(String username) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT 1 FROM users WHERE username=?");
            ps.setString(1, username);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    /** 返回所有注册用户名（不含 excludeUser） */
    public List<String> getAllUsers(String excludeUser) {
        List<String> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT username FROM users WHERE username<>? ORDER BY username");
            ps.setString(1, excludeUser);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("[DB] getAllUsers 失败: " + e.getMessage());
        }
        return list;
    }

    // ===== 好友 =====

    /** 发送好友请求（幂等） */
    public boolean sendFriendRequest(String from, String to) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "INSERT IGNORE INTO friend_requests(from_user,to_user,status) VALUES(?,?,'pending')");
            ps.setString(1, from);
            ps.setString(2, to);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[DB] sendFriendRequest 失败: " + e.getMessage());
            return false;
        }
    }

    /** 接受好友请求：双向写入 accepted */
    public boolean acceptFriendRequest(String from, String to) {
        try {
            Connection conn = getConn();
            conn.setAutoCommit(false);
            try {
                PreparedStatement ps1 = conn.prepareStatement(
                        "UPDATE friend_requests SET status='accepted' WHERE from_user=? AND to_user=?");
                ps1.setString(1, from); ps1.setString(2, to);
                ps1.executeUpdate();
                PreparedStatement ps2 = conn.prepareStatement(
                        "INSERT IGNORE INTO friend_requests(from_user,to_user,status) VALUES(?,?,'accepted')");
                ps2.setString(1, to); ps2.setString(2, from);
                ps2.executeUpdate();
                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[DB] acceptFriendRequest 失败: " + e.getMessage());
            return false;
        }
    }

    /** 拒绝/删除好友请求 */
    public void rejectFriendRequest(String from, String to) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "DELETE FROM friend_requests WHERE from_user=? AND to_user=?");
            ps.setString(1, from); ps.setString(2, to);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] rejectFriendRequest 失败: " + e.getMessage());
        }
    }

    /** 删除好友关系（双向删除） */
    public void deleteFriend(String a, String b) {
        try {
            Connection conn = getConn();
            conn.setAutoCommit(false);
            try {
                PreparedStatement ps1 = conn.prepareStatement(
                        "DELETE FROM friend_requests WHERE from_user=? AND to_user=?");
                ps1.setString(1, a); ps1.setString(2, b);
                ps1.executeUpdate();
                PreparedStatement ps2 = conn.prepareStatement(
                        "DELETE FROM friend_requests WHERE from_user=? AND to_user=?");
                ps2.setString(1, b); ps2.setString(2, a);
                ps2.executeUpdate();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[DB] deleteFriend 失败: " + e.getMessage());
        }
    }

    /** 返回 user 的所有已接受联系人用户名 */
    public List<String> getContacts(String user) {
        List<String> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT to_user FROM friend_requests WHERE from_user=? AND status='accepted'");
            ps.setString(1, user);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("[DB] getContacts 失败: " + e.getMessage());
        }
        return list;
    }

    /** 检查二人是否已是好友 */
    public boolean areFriends(String a, String b) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT 1 FROM friend_requests WHERE from_user=? AND to_user=? AND status='accepted'");
            ps.setString(1, a); ps.setString(2, b);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    /** 返回所有向 user 发送的待处理请求的发起人列表 */
    public List<String> getPendingRequestsTo(String user) {
        List<String> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT from_user FROM friend_requests WHERE to_user=? AND status='pending'");
            ps.setString(1, user);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("[DB] getPendingRequestsTo 失败: " + e.getMessage());
        }
        return list;
    }

    /** 检查是否已发过请求（pending）*/
    public boolean hasPendingRequest(String from, String to) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT 1 FROM friend_requests WHERE from_user=? AND to_user=? AND status='pending'");
            ps.setString(1, from); ps.setString(2, to);
            return ps.executeQuery().next();
        } catch (SQLException e) { return false; }
    }

    // ===== 消息 =====

    public void saveMessage(Message msg) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "INSERT INTO messages(from_user,to_user,content,send_time) VALUES(?,?,?,?)");
            ps.setString(1, msg.getFrom());
            ps.setString(2, msg.getTo());
            ps.setString(3, msg.getContent());
            ps.setLong(4, msg.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] saveMessage 失败: " + e.getMessage());
        }
    }

    public List<Message> getHistory(String user1, String user2) {
        List<Message> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT from_user,to_user,content,send_time FROM messages " +
                    "WHERE (from_user=? AND to_user=?) OR (from_user=? AND to_user=?) " +
                    "ORDER BY send_time ASC");
            ps.setString(1, user1); ps.setString(2, user2);
            ps.setString(3, user2); ps.setString(4, user1);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Message(Message.CHAT,
                        rs.getString("from_user"), rs.getString("to_user"),
                        rs.getString("content"),  rs.getLong("send_time")));
            }
        } catch (SQLException e) {
            System.err.println("[DB] getHistory 失败: " + e.getMessage());
        }
        return list;
    }

    /** 搜索聊天记录 */
    public List<Message> searchMessages(String user1, String user2, String keyword) {
        List<Message> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT from_user,to_user,content,send_time FROM messages " +
                    "WHERE ((from_user=? AND to_user=?) OR (from_user=? AND to_user=?)) " +
                    "AND content LIKE ? ORDER BY send_time ASC");
            ps.setString(1, user1); ps.setString(2, user2);
            ps.setString(3, user2); ps.setString(4, user1);
            ps.setString(5, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Message(Message.CHAT,
                        rs.getString("from_user"), rs.getString("to_user"),
                        rs.getString("content"),  rs.getLong("send_time")));
            }
        } catch (SQLException e) {
            System.err.println("[DB] searchMessages 失败: " + e.getMessage());
        }
        return list;
    }

    // ===== 群聊 =====

    /** 创建群聊，返回群ID */
    public int createGroup(String groupName, String creator, List<String> members) {
        try {
            Connection conn = getConn();
            conn.setAutoCommit(false);
            try {
                PreparedStatement ps1 = conn.prepareStatement(
                        "INSERT INTO chat_groups(group_name,creator) VALUES(?,?)",
                        Statement.RETURN_GENERATED_KEYS);
                ps1.setString(1, groupName);
                ps1.setString(2, creator);
                ps1.executeUpdate();
                ResultSet keys = ps1.getGeneratedKeys();
                keys.next();
                int groupId = keys.getInt(1);

                PreparedStatement ps2 = conn.prepareStatement(
                        "INSERT INTO group_members(group_id,username) VALUES(?,?)");
                // 创建者也加入群
                ps2.setInt(1, groupId); ps2.setString(2, creator);
                ps2.executeUpdate();
                for (String m : members) {
                    ps2.setInt(1, groupId); ps2.setString(2, m);
                    ps2.executeUpdate();
                }
                conn.commit();
                return groupId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[DB] createGroup 失败: " + e.getMessage());
            return -1;
        }
    }

    /** 获取用户所在的所有群 id:name */
    public List<String> getUserGroups(String username) {
        List<String> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT g.group_id, g.group_name FROM chat_groups g " +
                    "JOIN group_members m ON g.group_id=m.group_id " +
                    "WHERE m.username=? ORDER BY g.group_id");
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(rs.getInt(1) + ":" + rs.getString(2));
            }
        } catch (SQLException e) {
            System.err.println("[DB] getUserGroups 失败: " + e.getMessage());
        }
        return list;
    }

    /** 获取群成员 */
    public List<String> getGroupMembers(int groupId) {
        List<String> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT username FROM group_members WHERE group_id=?");
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) list.add(rs.getString(1));
        } catch (SQLException e) {
            System.err.println("[DB] getGroupMembers 失败: " + e.getMessage());
        }
        return list;
    }

    /** 保存群消息 */
    public void saveGroupMessage(int groupId, Message msg) {
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "INSERT INTO group_messages(group_id,from_user,content,send_time) VALUES(?,?,?,?)");
            ps.setInt(1, groupId);
            ps.setString(2, msg.getFrom());
            ps.setString(3, msg.getContent());
            ps.setLong(4, msg.getTimestamp());
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[DB] saveGroupMessage 失败: " + e.getMessage());
        }
    }

    /** 获取群聊历史 */
    public List<Message> getGroupHistory(int groupId) {
        List<Message> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT from_user,content,send_time FROM group_messages " +
                    "WHERE group_id=? ORDER BY send_time ASC");
            ps.setInt(1, groupId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Message(Message.GROUP_CHAT,
                        rs.getString("from_user"), String.valueOf(groupId),
                        rs.getString("content"), rs.getLong("send_time")));
            }
        } catch (SQLException e) {
            System.err.println("[DB] getGroupHistory 失败: " + e.getMessage());
        }
        return list;
    }

    /** 搜索群聊记录 */
    public List<Message> searchGroupMessages(int groupId, String keyword) {
        List<Message> list = new ArrayList<>();
        try {
            PreparedStatement ps = getConn().prepareStatement(
                    "SELECT from_user,content,send_time FROM group_messages " +
                    "WHERE group_id=? AND content LIKE ? ORDER BY send_time ASC");
            ps.setInt(1, groupId);
            ps.setString(2, "%" + keyword + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(new Message(Message.GROUP_CHAT,
                        rs.getString("from_user"), String.valueOf(groupId),
                        rs.getString("content"), rs.getLong("send_time")));
            }
        } catch (SQLException e) {
            System.err.println("[DB] searchGroupMessages 失败: " + e.getMessage());
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

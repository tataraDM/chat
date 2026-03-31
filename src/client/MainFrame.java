package client;

import common.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainFrame extends JFrame implements ChatClient.MessageListener {
    private static final Color PRIMARY   = new Color(0x07C160);
    private static final Color HEADER_BG = new Color(0xEDEDED);
    private static final Color LIST_BG   = new Color(0xF7F7F7);
    private static final Color HOVER_BG  = new Color(0xE0E0E0);

    private final ChatClient client;
    private final Map<String, ChatFrame> openChats = new ConcurrentHashMap<>();
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    // 已接受的联系人
    private final Set<String> contacts   = new LinkedHashSet<>();
    // 当前在线用户
    private final Set<String> onlineUsers = new HashSet<>();
    private JLabel statusLabel;

    // 等待 ALL_USERS_RESP 时临时存储
    private volatile String[] pendingAllUsers = null;
    private final Object allUsersLock = new Object();

    public MainFrame(ChatClient client) {
        this.client = client;
        client.setListener(this);
        setTitle("ChaTTE - " + client.getUsername());
        setSize(320, 600);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                client.disconnect();
                dispose();
                System.exit(0);
            }
        });
        setContentPane(buildUI());
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(LIST_BG);

        // === 顶部 ===
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel avatarArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        avatarArea.setOpaque(false);
        avatarArea.add(makeAvatar(client.getUsername(), 44));

        JPanel namePanel = new JPanel();
        namePanel.setOpaque(false);
        namePanel.setLayout(new BoxLayout(namePanel, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(client.getUsername());
        nameLabel.setFont(new Font("微软雅黑", Font.BOLD, 14));
        statusLabel = new JLabel("在线");
        statusLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        statusLabel.setForeground(PRIMARY);
        namePanel.add(nameLabel);
        namePanel.add(statusLabel);
        avatarArea.add(namePanel);

        // 添加联系人按钮
        JButton addBtn = new JButton("+");
        addBtn.setFont(new Font("Arial", Font.BOLD, 18));
        addBtn.setForeground(PRIMARY);
        addBtn.setBorderPainted(false);
        addBtn.setContentAreaFilled(false);
        addBtn.setFocusPainted(false);
        addBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addBtn.setToolTipText("添加联系人");
        addBtn.addActionListener(e -> openAddContactDialog());

        header.add(avatarArea, BorderLayout.CENTER);
        header.add(addBtn, BorderLayout.EAST);

        // === 联系人列表 ===
        JList<String> userList = new JList<>(userModel);
        userList.setCellRenderer(new UserCellRenderer());
        userList.setBackground(LIST_BG);
        userList.setFixedCellHeight(64);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBorder(null);
        userList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                String target = userList.getSelectedValue();
                if (target != null) openChat(target);
            }
        });

        JScrollPane scroll = new JScrollPane(userList);
        scroll.setBorder(null);

        JLabel listHeader = new JLabel("  联系人");
        listHeader.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        listHeader.setForeground(Color.GRAY);
        listHeader.setBorder(new EmptyBorder(6, 8, 6, 8));
        listHeader.setOpaque(true);
        listHeader.setBackground(LIST_BG);

        JPanel center = new JPanel(new BorderLayout());
        center.add(listHeader, BorderLayout.NORTH);
        center.add(scroll, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        return root;
    }

    private void openChat(String target) {
        ChatFrame frame = openChats.computeIfAbsent(target, t -> {
            ChatFrame cf = new ChatFrame(client, t, onlineUsers.contains(t));
            cf.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) { openChats.remove(t); }
            });
            cf.setVisible(true);
            return cf;
        });
        frame.toFront();
        frame.requestFocus();
    }

    // ===== MessageListener =====

    @Override
    public void onMessage(Message msg) {
        SwingUtilities.invokeLater(() -> dispatch(msg));
    }

    private void dispatch(Message msg) {
        switch (msg.getType()) {

            case Message.USER_LIST:
                onlineUsers.clear();
                if (!msg.getContent().isEmpty())
                    Collections.addAll(onlineUsers, msg.getContent().split(","));
                refreshList();
                break;

            case Message.CONTACTS:
                contacts.clear();
                if (!msg.getContent().isEmpty())
                    Collections.addAll(contacts, msg.getContent().split(","));
                refreshList();
                break;

            case Message.CHAT: {
                String peer = msg.getFrom().equals(client.getUsername())
                        ? msg.getTo() : msg.getFrom();
                ChatFrame cf = openChats.get(peer);
                if (cf != null) {
                    cf.receiveMessage(msg);
                } else {
                    // Bug2 修复：直接打开窗口，不再调 receiveMessage
                    // 历史记录请求会包含这条消息
                    openChat(peer);
                }
                break;
            }

            case Message.HISTORY_RESP: {
                String histPeer = "__END__".equals(msg.getFrom())
                        ? msg.getTo()
                        : (msg.getFrom().equals(client.getUsername()) ? msg.getTo() : msg.getFrom());
                ChatFrame hcf = openChats.get(histPeer);
                if (hcf != null) hcf.receiveHistory(msg);
                break;
            }

            case Message.ALL_USERS_RESP: {
                String csv = msg.getContent();
                String[] all = csv.isEmpty() ? new String[0] : csv.split(",");
                showAddContactDialog(all);
                break;
            }

            case Message.FRIEND_REQ: {
                String requester = msg.getFrom();
                int choice = JOptionPane.showConfirmDialog(this,
                        requester + " 请求添加你为联系人",
                        "好友请求", JOptionPane.YES_NO_OPTION);
                String ans = (choice == JOptionPane.YES_OPTION) ? "accept" : "reject";
                client.send(new Message(Message.FRIEND_ACCEPT,
                        client.getUsername(), requester, ans));
                break;
            }

            case Message.FRIEND_ACCEPT:
                if ("accept".equals(msg.getContent())) {
                    JOptionPane.showMessageDialog(this,
                            msg.getFrom() + " 接受了你的好友请求", "提示",
                            JOptionPane.INFORMATION_MESSAGE);
                }
                break;

            case Message.FRIEND_REJECT:
                JOptionPane.showMessageDialog(this, msg.getContent(),
                        "提示", JOptionPane.INFORMATION_MESSAGE);
                break;

            default:
                break;
        }
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("已断开");
            statusLabel.setForeground(Color.RED);
        });
    }

    private void refreshList() {
        userModel.clear();
        contacts.forEach(userModel::addElement);
    }

    // ===== 添加联系人 =====

    private void openAddContactDialog() {
        client.send(new Message(Message.ALL_USERS_REQ,
                client.getUsername(), "server", ""));
        // 结果通过 ALL_USERS_RESP 回调到 showAddContactDialog
    }

    private void showAddContactDialog(String[] allUsers) {
        // 过滤掉已是联系人和自己
        java.util.List<String> candidates = new ArrayList<>();
        for (String u : allUsers) {
            if (!contacts.contains(u) && !u.equals(client.getUsername()))
                candidates.add(u);
        }

        JDialog dialog = new JDialog(this, "添加联系人", true);
        dialog.setSize(300, 420);
        dialog.setLocationRelativeTo(this);
        dialog.setLayout(new BorderLayout());

        JLabel hint = new JLabel("选择要添加的用户", SwingConstants.CENTER);
        hint.setBorder(new EmptyBorder(12, 0, 8, 0));
        hint.setFont(new Font("微软雅黑", Font.BOLD, 13));

        if (candidates.isEmpty()) {
            dialog.add(hint, BorderLayout.NORTH);
            dialog.add(new JLabel("暂无可添加的用户", SwingConstants.CENTER), BorderLayout.CENTER);
            dialog.setVisible(true);
            return;
        }

        DefaultListModel<String> model = new DefaultListModel<>();
        candidates.forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setCellRenderer(new UserCellRenderer());
        list.setFixedCellHeight(56);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(LIST_BG);

        JButton sendBtn = new JButton("发送好友请求");
        sendBtn.setFont(new Font("微软雅黑", Font.BOLD, 13));
        sendBtn.setBackground(PRIMARY);
        sendBtn.setForeground(Color.WHITE);
        sendBtn.setFocusPainted(false);
        sendBtn.setBorder(new EmptyBorder(10, 0, 10, 0));
        sendBtn.addActionListener(e -> {
            String target = list.getSelectedValue();
            if (target == null) {
                JOptionPane.showMessageDialog(dialog, "请先选择一个用户");
                return;
            }
            client.send(new Message(Message.FRIEND_REQ,
                    client.getUsername(), target, ""));
            dialog.dispose();
        });

        dialog.add(hint, BorderLayout.NORTH);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.add(sendBtn, BorderLayout.SOUTH);
        dialog.setVisible(true);
    }

    // ===== 头像 =====

    static JLabel makeAvatar(String name, int size) {
        JLabel av = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int hash = name.hashCode();
                float hue = Math.abs(hash % 360) / 360f;
                g2.setColor(Color.getHSBColor(hue, 0.55f, 0.85f));
                g2.fillOval(0, 0, size, size);
                g2.setColor(Color.WHITE);
                int fs = size / 2 - 2;
                g2.setFont(new Font("微软雅黑", Font.BOLD, fs));
                String init = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(init, (size - fm.stringWidth(init)) / 2,
                        (size + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        av.setPreferredSize(new Dimension(size, size));
        av.setMinimumSize(new Dimension(size, size));
        av.setMaximumSize(new Dimension(size, size));
        return av;
    }

    // ===== Cell Renderer =====

    private class UserCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel nameLabel = new JLabel();
        private final JLabel dotLabel  = new JLabel();
        private String currentName = "";

        UserCellRenderer() {
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(8, 14, 8, 14));
            nameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            dotLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));

            JLabel avHolder = new JLabel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int hash = currentName.hashCode();
                    float hue = Math.abs(hash % 360) / 360f;
                    g2.setColor(Color.getHSBColor(hue, 0.55f, 0.85f));
                    g2.fillOval(0, 0, 44, 44);
                    g2.setColor(Color.WHITE);
                    g2.setFont(new Font("微软雅黑", Font.BOLD, 17));
                    String init = currentName.isEmpty() ? "?" : currentName.substring(0, 1).toUpperCase();
                    FontMetrics fm = g2.getFontMetrics();
                    g2.drawString(init, (44 - fm.stringWidth(init)) / 2,
                            (44 + fm.getAscent() - fm.getDescent()) / 2);
                }
            };
            avHolder.setPreferredSize(new Dimension(44, 44));

            JPanel right = new JPanel();
            right.setOpaque(false);
            right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
            right.setBorder(new EmptyBorder(0, 12, 0, 0));
            right.add(nameLabel);
            right.add(dotLabel);

            add(avHolder, BorderLayout.WEST);
            add(right, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                String value, int index, boolean isSelected, boolean cellHasFocus) {
            currentName = value == null ? "" : value;
            nameLabel.setText(currentName);
            boolean online = onlineUsers.contains(currentName);
            dotLabel.setText(online ? "● 在线" : "○ 离线");
            dotLabel.setForeground(online ? PRIMARY : Color.GRAY);
            dotLabel.setFont(new Font("微软雅黑", Font.PLAIN, 11));
            setBackground(isSelected ? HOVER_BG : LIST_BG);
            setOpaque(true);
            return this;
        }
    }
}

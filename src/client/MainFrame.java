package client;

import common.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainFrame extends JFrame implements ChatClient.MessageListener {
    private static final Color PRIMARY    = new Color(0x07C160);
    private static final Color HEADER_BG  = new Color(0xEDEDED);
    private static final Color LIST_BG    = new Color(0xF7F7F7);
    private static final Color HOVER_BG   = new Color(0xE0E0E0);
    private static final Color ONLINE_DOT = new Color(0x07C160);

    private final ChatClient client;
    private final Map<String, ChatFrame> openChats = new ConcurrentHashMap<>();
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final Set<String> onlineUsers = new LinkedHashSet<>();
    private JLabel statusLabel;

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

        // === 顶部：我的信息栏 ===
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel avatarArea = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        avatarArea.setOpaque(false);

        JLabel avatarLabel = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PRIMARY);
                g2.fillOval(0, 0, 44, 44);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("微软雅黑", Font.BOLD, 18));
                String initial = client.getUsername().substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(initial, (44 - fm.stringWidth(initial)) / 2,
                        (44 + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        avatarLabel.setPreferredSize(new Dimension(44, 44));

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

        avatarArea.add(avatarLabel);
        avatarArea.add(namePanel);
        header.add(avatarArea, BorderLayout.CENTER);

        // === 用户列表 ===
        JList<String> userList = new JList<>(userModel);
        userList.setCellRenderer(new UserCellRenderer());
        userList.setBackground(LIST_BG);
        userList.setFixedCellHeight(64);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setBorder(null);
        userList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() >= 1) {
                    String target = userList.getSelectedValue();
                    if (target != null && !target.equals(client.getUsername())) {
                        openChat(target);
                    }
                }
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

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(listHeader, BorderLayout.NORTH);
        centerPanel.add(scroll, BorderLayout.CENTER);

        root.add(header, BorderLayout.NORTH);
        root.add(centerPanel, BorderLayout.CENTER);
        return root;
    }

    private void openChat(String target) {
        ChatFrame frame = openChats.computeIfAbsent(target, t -> {
            ChatFrame cf = new ChatFrame(client, t, onlineUsers.contains(t));
            cf.addWindowListener(new WindowAdapter() {
                @Override public void windowClosed(WindowEvent e) {
                    openChats.remove(t);
                }
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
        SwingUtilities.invokeLater(() -> {
            switch (msg.getType()) {
                case Message.USER_LIST:
                    updateUserList(msg.getContent());
                    break;
                case Message.CHAT:
                    // 收到消息，转发给对应 ChatFrame（或打开新窗口）
                    String peer = msg.getFrom().equals(client.getUsername()) ? msg.getTo() : msg.getFrom();
                    ChatFrame cf = openChats.get(peer);
                    if (cf != null) {
                        cf.receiveMessage(msg);
                    } else {
                        // 未打开聊天窗口时，打开并追加
                        openChat(peer);
                        ChatFrame newCf = openChats.get(peer);
                        if (newCf != null) newCf.receiveMessage(msg);
                    }
                    break;
                case Message.HISTORY_RESP:
                    // 转发给对应的 ChatFrame
                    String histPeer = msg.getFrom().equals("__END__") ? msg.getTo()
                            : (msg.getFrom().equals(client.getUsername()) ? msg.getTo() : msg.getFrom());
                    ChatFrame hcf = openChats.get(histPeer);
                    if (hcf != null) hcf.receiveHistory(msg);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    public void onDisconnected() {
        SwingUtilities.invokeLater(() -> {
            statusLabel.setText("已断开");
            statusLabel.setForeground(Color.RED);
        });
    }

    private void updateUserList(String csv) {
        Set<String> current = new LinkedHashSet<>();
        if (!csv.isEmpty()) {
            for (String u : csv.split(",")) current.add(u.trim());
        }
        onlineUsers.clear();
        onlineUsers.addAll(current);

        userModel.clear();
        current.stream()
               .filter(u -> !u.equals(client.getUsername()))
               .forEach(userModel::addElement);
    }

    // ===== Cell Renderer =====

    private class UserCellRenderer extends JPanel implements ListCellRenderer<String> {
        private final JLabel avatarLabel;
        private final JLabel nameLabel;
        private final JLabel statusDot;
        private String currentName = "";

        UserCellRenderer() {
            setLayout(new BorderLayout());
            setBorder(new EmptyBorder(8, 14, 8, 14));

            avatarLabel = new JLabel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    // 随机但固定颜色
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
            avatarLabel.setPreferredSize(new Dimension(44, 44));

            statusDot = new JLabel() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g;
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(onlineUsers.contains(currentName) ? ONLINE_DOT : Color.LIGHT_GRAY);
                    g2.fillOval(1, 1, 10, 10);
                }
            };
            statusDot.setPreferredSize(new Dimension(12, 12));

            nameLabel = new JLabel();
            nameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));

            JPanel right = new JPanel(new GridBagLayout());
            right.setOpaque(false);
            right.setBorder(new EmptyBorder(0, 12, 0, 0));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0; gbc.gridy = 0; gbc.anchor = GridBagConstraints.WEST; gbc.weightx = 1;
            right.add(nameLabel, gbc);
            gbc.gridy = 1;
            right.add(statusDot, gbc);

            add(avatarLabel, BorderLayout.WEST);
            add(right, BorderLayout.CENTER);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends String> list,
                String value, int index, boolean isSelected, boolean cellHasFocus) {
            currentName = value == null ? "" : value;
            nameLabel.setText(currentName);
            setBackground(isSelected ? HOVER_BG : LIST_BG);
            setOpaque(true);
            return this;
        }
    }
}

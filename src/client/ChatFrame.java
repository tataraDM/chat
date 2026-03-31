package client;

import common.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;

public class ChatFrame extends JFrame {
    private static final Color PRIMARY      = new Color(0x07C160);
    private static final Color HEADER_BG    = new Color(0xEDEDED);
    private static final Color CHAT_BG      = new Color(0xF0F0F0);
    private static final Color BUBBLE_MINE  = new Color(0x95EC69);  // 我的消息（绿色）
    private static final Color BUBBLE_OTHER = Color.WHITE;           // 对方消息（白色）
    private static final Color INPUT_BG     = new Color(0xFAFAFA);

    private final ChatClient client;
    private final String peerName;
    private boolean peerOnline;

    private JPanel messagePanel;    // 装所有气泡
    private JScrollPane scrollPane;
    private JTextArea inputArea;
    private boolean historyLoaded = false;
    private final List<Message> pendingHistory = new ArrayList<>();
    private final List<Message> pendingRealtime = new ArrayList<>();

    public ChatFrame(ChatClient client, String peerName, boolean peerOnline) {
        this.client    = client;
        this.peerName  = peerName;
        this.peerOnline = peerOnline;

        setTitle(peerName);
        setSize(480, 640);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setContentPane(buildUI());

        // 请求历史记录
        client.requestHistory(peerName);
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout());

        // === 顶部标题栏 ===
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(HEADER_BG);
        header.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel peerLabel = new JLabel(peerName);
        peerLabel.setFont(new Font("微软雅黑", Font.BOLD, 15));
        JLabel peerStatus = new JLabel(peerOnline ? "在线" : "离线");
        peerStatus.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        peerStatus.setForeground(peerOnline ? PRIMARY : Color.GRAY);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.add(peerLabel);
        titleBox.add(peerStatus);
        header.add(titleBox, BorderLayout.CENTER);

        // === 消息区 ===
        messagePanel = new JPanel();
        messagePanel.setLayout(new BoxLayout(messagePanel, BoxLayout.Y_AXIS));
        messagePanel.setBackground(CHAT_BG);
        messagePanel.setBorder(new EmptyBorder(8, 8, 8, 8));

        scrollPane = new JScrollPane(messagePanel);
        scrollPane.setBorder(null);
        scrollPane.setBackground(CHAT_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // === 输入区 ===
        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.setBackground(INPUT_BG);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xDDDDDD)),
            new EmptyBorder(8, 10, 8, 10)));

        inputArea = new JTextArea(3, 0);
        inputArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(null);
        inputArea.setBackground(INPUT_BG);
        // Ctrl+Enter 或 Enter 发送（Enter 换行用 Shift+Enter）
        inputArea.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    doSend();
                }
            }
        });

        JButton sendBtn = new JButton("发送") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isPressed() ? new Color(0x05A050) : PRIMARY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("微软雅黑", Font.BOLD, 13));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        sendBtn.setPreferredSize(new Dimension(72, 32));
        sendBtn.setBorderPainted(false);
        sendBtn.setContentAreaFilled(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> doSend());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 4));
        btnRow.setOpaque(false);
        btnRow.add(sendBtn);

        JLabel hint = new JLabel("Enter 发送   Shift+Enter 换行");
        hint.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        hint.setForeground(Color.GRAY);

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(null);
        inputScroll.setBackground(INPUT_BG);

        inputPanel.add(hint, BorderLayout.NORTH);
        inputPanel.add(inputScroll, BorderLayout.CENTER);
        inputPanel.add(btnRow, BorderLayout.SOUTH);

        root.add(header, BorderLayout.NORTH);
        root.add(scrollPane, BorderLayout.CENTER);
        root.add(inputPanel, BorderLayout.SOUTH);
        return root;
    }

    private void doSend() {
        String text = inputArea.getText().trim();
        if (text.isEmpty()) return;
        inputArea.setText("");
        Message msg = new Message(Message.CHAT, client.getUsername(), peerName, text);
        client.send(msg);
        addBubble(msg, true);
    }

    /** 接收来自 MainFrame 转发的实时消息 */
    public void receiveMessage(Message msg) {
        SwingUtilities.invokeLater(() -> addBubble(msg, msg.getFrom().equals(client.getUsername())));
    }

    /** 接收历史消息（由 MainFrame 转发） */
    public void receiveHistory(Message msg) {
        SwingUtilities.invokeLater(() -> {
            if ("__END__".equals(msg.getFrom())) {
                // 历史加载完毕，将缓存的历史按顺序插入最前
                historyLoaded = true;
                // 清空当前面板，先放历史再放 pending
                Component[] existing = messagePanel.getComponents();
                messagePanel.removeAll();
                for (Message h : pendingHistory) {
                    addBubbleInternal(h, h.getFrom().equals(client.getUsername()));
                }
                for (Component c : existing) messagePanel.add(c);
                pendingHistory.clear();
                // 补充历史加载期间收到的实时消息
                for (Message r : pendingRealtime) {
                    addBubbleInternal(r, r.getFrom().equals(client.getUsername()));
                }
                pendingRealtime.clear();
                messagePanel.revalidate();
                scrollToBottom();
            } else {
                pendingHistory.add(msg);
            }
        });
    }

    private void addBubble(Message msg, boolean isMine) {
        if (!historyLoaded) {
            pendingRealtime.add(msg);
            return;
        }
        addBubbleInternal(msg, isMine);
        scrollToBottom();
    }

    private void addBubbleInternal(Message msg, boolean isMine) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 4, 4, 4));

        // 时间戳
        JLabel timeLabel = new JLabel(msg.getFormattedTime());
        timeLabel.setFont(new Font("微软雅黑", Font.PLAIN, 10));
        timeLabel.setForeground(Color.GRAY);
        timeLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // 气泡
        BubbleLabel bubble = new BubbleLabel(msg.getContent(), isMine);

        // 头像
        String name = isMine ? client.getUsername() : peerName;
        JLabel avatar = makeAvatar(name);

        JPanel bubbleRow = new JPanel(new FlowLayout(isMine ? FlowLayout.RIGHT : FlowLayout.LEFT, 6, 0));
        bubbleRow.setOpaque(false);
        if (isMine) {
            bubbleRow.add(bubble);
            bubbleRow.add(avatar);
        } else {
            bubbleRow.add(avatar);
            bubbleRow.add(bubble);
        }

        row.add(timeLabel, BorderLayout.NORTH);
        row.add(bubbleRow, BorderLayout.CENTER);
        messagePanel.add(row);
        messagePanel.revalidate();
    }

    private JLabel makeAvatar(String name) {
        JLabel av = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int hash = name.hashCode();
                float hue = Math.abs(hash % 360) / 360f;
                g2.setColor(Color.getHSBColor(hue, 0.55f, 0.85f));
                g2.fillOval(0, 0, 34, 34);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("微软雅黑", Font.BOLD, 14));
                String init = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(init, (34 - fm.stringWidth(init)) / 2,
                        (34 + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        av.setPreferredSize(new Dimension(34, 34));
        return av;
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    // ===== 气泡组件 =====
    private static class BubbleLabel extends JPanel {
        private final String text;
        private final boolean isMine;
        private static final int ARC    = 14;
        private static final int PAD_H  = 10;
        private static final int PAD_V  = 7;
        private static final int MAX_W  = 260;

        BubbleLabel(String text, boolean isMine) {
            this.text   = text;
            this.isMine = isMine;
            setOpaque(false);
            Font font = new Font("微软雅黑", Font.PLAIN, 14);
            // 计算尺寸
            FontMetrics fm = getFontMetrics(font);
            List<String> lines = wrapText(text, fm, MAX_W - PAD_H * 2);
            int w = 0;
            for (String l : lines) w = Math.max(w, fm.stringWidth(l));
            w = Math.min(w, MAX_W - PAD_H * 2) + PAD_H * 2 + 2;
            int h = lines.size() * fm.getHeight() + PAD_V * 2 + 2;
            setPreferredSize(new Dimension(w, h));
            setFont(font);
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 背景气泡
            g2.setColor(isMine ? BUBBLE_MINE : BUBBLE_OTHER);
            g2.fill(new RoundRectangle2D.Float(1, 1, getWidth() - 2, getHeight() - 2, ARC, ARC));

            // 文字
            g2.setColor(Color.BLACK);
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            List<String> lines = wrapText(text, fm, MAX_W - PAD_H * 2);
            int y = PAD_V + fm.getAscent();
            for (String line : lines) {
                g2.drawString(line, PAD_H, y);
                y += fm.getHeight();
            }
        }

        private List<String> wrapText(String text, FontMetrics fm, int maxWidth) {
            List<String> lines = new ArrayList<>();
            String[] paragraphs = text.split("\n", -1);
            for (String para : paragraphs) {
                if (para.isEmpty()) { lines.add(""); continue; }
                StringBuilder sb = new StringBuilder();
                for (char c : para.toCharArray()) {
                    sb.append(c);
                    if (fm.stringWidth(sb.toString()) > maxWidth) {
                        sb.deleteCharAt(sb.length() - 1);
                        lines.add(sb.toString());
                        sb = new StringBuilder(String.valueOf(c));
                    }
                }
                if (sb.length() > 0) lines.add(sb.toString());
            }
            return lines;
        }
    }
}

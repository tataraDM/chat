package client;

import common.Message;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

public class LoginFrame extends JFrame {
    private static final Color PRIMARY   = new Color(0x07C160);
    private static final Color BG_TOP    = new Color(0x1AAD19);
    private static final Color BG_BOTTOM = new Color(0x0B7D09);

    private JTextField     usernameField;
    private JPasswordField passwordField;
    private JButton        loginBtn;
    private ChatClient     connectedClient;

    public LoginFrame() {
        setTitle("ChaTTE - 登录");
        setSize(380, 540);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        setContentPane(buildUI());
    }

    private JPanel buildUI() {
        JPanel root = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }
        };

        // --- 顶部 Logo ---
        JPanel topPanel = new JPanel();
        topPanel.setOpaque(false);
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(new EmptyBorder(55, 0, 35, 0));

        JLabel avatar = new JLabel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillOval(0, 0, 80, 80);
                g2.setColor(PRIMARY);
                g2.setFont(new Font("Arial", Font.BOLD, 36));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("C", (80 - fm.stringWidth("C")) / 2, (80 + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        avatar.setPreferredSize(new Dimension(80, 80));
        avatar.setMinimumSize(new Dimension(80, 80));
        avatar.setMaximumSize(new Dimension(80, 80));
        avatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel appName = new JLabel("ChaTTE");
        appName.setFont(new Font("Arial", Font.BOLD, 28));
        appName.setForeground(Color.WHITE);
        appName.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subtitle = new JLabel("即时通信应用");
        subtitle.setFont(new Font("微软雅黑", Font.PLAIN, 13));
        subtitle.setForeground(new Color(255, 255, 255, 180));
        subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        topPanel.add(avatar);
        topPanel.add(Box.createVerticalStrut(12));
        topPanel.add(appName);
        topPanel.add(Box.createVerticalStrut(4));
        topPanel.add(subtitle);

        // --- 表单卡片 ---
        JPanel formCard = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 20, 20));
            }
        };
        formCard.setOpaque(false);
        formCard.setLayout(new BoxLayout(formCard, BoxLayout.Y_AXIS));
        formCard.setBorder(new EmptyBorder(24, 32, 24, 32));

        usernameField = new JTextField();
        styleField(usernameField, "用户名");

        passwordField = new JPasswordField();
        styleField(passwordField, "密码");

        loginBtn = makeButton("登 录", PRIMARY);
        loginBtn.addActionListener(e -> doLogin());
        usernameField.addActionListener(e -> doLogin());
        passwordField.addActionListener(e -> doLogin());

        JPanel regRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        regRow.setOpaque(false);
        JLabel regHint = new JLabel("还没有账号？");
        regHint.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        regHint.setForeground(Color.GRAY);
        JButton regBtn = new JButton("立即注册");
        regBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        regBtn.setForeground(PRIMARY);
        regBtn.setBorderPainted(false);
        regBtn.setContentAreaFilled(false);
        regBtn.setFocusPainted(false);
        regBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        regBtn.addActionListener(e -> doRegister());
        regRow.add(regHint);
        regRow.add(regBtn);

        formCard.add(usernameField);
        formCard.add(Box.createVerticalStrut(14));
        formCard.add(passwordField);
        formCard.add(Box.createVerticalStrut(20));
        formCard.add(loginBtn);
        formCard.add(Box.createVerticalStrut(12));
        formCard.add(regRow);

        JPanel formWrapper = new JPanel(new BorderLayout());
        formWrapper.setOpaque(false);
        formWrapper.setBorder(new EmptyBorder(0, 24, 36, 24));
        formWrapper.add(formCard, BorderLayout.CENTER);

        root.add(topPanel, BorderLayout.CENTER);
        root.add(formWrapper, BorderLayout.SOUTH);
        return root;
    }

    private void styleField(JTextField f, String placeholder) {
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        f.setPreferredSize(new Dimension(0, 44));
        f.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xDDDDDD)),
            new EmptyBorder(0, 4, 0, 4)));
        f.setForeground(Color.GRAY);
        f.setText(placeholder);
        f.addFocusListener(new FocusAdapter() {
            @Override public void focusGained(FocusEvent e) {
                if (f.getText().equals(placeholder)) {
                    f.setText("");
                    f.setForeground(Color.BLACK);
                    if (f instanceof JPasswordField) ((JPasswordField) f).setEchoChar('\u25cf');
                }
            }
            @Override public void focusLost(FocusEvent e) {
                if (f.getText().isEmpty()) {
                    f.setForeground(Color.GRAY);
                    f.setText(placeholder);
                    if (f instanceof JPasswordField) ((JPasswordField) f).setEchoChar((char) 0);
                }
            }
        });
        if (f instanceof JPasswordField) ((JPasswordField) f).setEchoChar((char) 0);
    }

    private JButton makeButton(String text, Color color) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? (getModel().isPressed() ? color.darker() : color) : Color.LIGHT_GRAY);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("微软雅黑", Font.BOLD, 15));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(getText(),
                    (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        btn.setPreferredSize(new Dimension(0, 44));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private String getFieldText(JTextField f, String placeholder) {
        String t = f.getText();
        return t.equals(placeholder) ? "" : t.trim();
    }

    private void doLogin() {
        String name = getFieldText(usernameField, "用户名");
        String pwd  = getFieldText(passwordField, "密码");
        if (name.isEmpty()) { JOptionPane.showMessageDialog(this, "请输入用户名"); return; }
        if (pwd.isEmpty())  { JOptionPane.showMessageDialog(this, "请输入密码");   return; }

        loginBtn.setEnabled(false);
        loginBtn.setText("连接中...");

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                ChatClient client = new ChatClient(name);
                String err = client.connect(pwd);
                if (err == null) connectedClient = client;
                return err;
            }
            @Override protected void done() {
                try {
                    String err = get();
                    if (err == null) {
                        new MainFrame(connectedClient).setVisible(true);
                        dispose();
                    } else {
                        JOptionPane.showMessageDialog(LoginFrame.this, err, "登录失败", JOptionPane.ERROR_MESSAGE);
                        loginBtn.setEnabled(true);
                        loginBtn.setText("登 录");
                    }
                } catch (Exception ex) {
                    loginBtn.setEnabled(true);
                    loginBtn.setText("登 录");
                }
            }
        }.execute();
    }

    private void doRegister() {
        // 弹出注册对话框
        JPanel panel = new JPanel(new GridLayout(4, 2, 8, 10));
        panel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JTextField regName = new JTextField();
        JPasswordField regPwd  = new JPasswordField();
        JPasswordField regPwd2 = new JPasswordField();
        panel.add(new JLabel("用户名:"));       panel.add(regName);
        panel.add(new JLabel("密码:"));         panel.add(regPwd);
        panel.add(new JLabel("确认密码:"));     panel.add(regPwd2);

        int result = JOptionPane.showConfirmDialog(this, panel, "注册新账号",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;

        String name = regName.getText().trim();
        String pwd  = new String(regPwd.getPassword());
        String pwd2 = new String(regPwd2.getPassword());

        if (name.isEmpty()) { JOptionPane.showMessageDialog(this, "用户名不能为空"); return; }
        if (pwd.isEmpty())  { JOptionPane.showMessageDialog(this, "密码不能为空");   return; }
        if (!pwd.equals(pwd2)) { JOptionPane.showMessageDialog(this, "两次密码不一致"); return; }

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return new ChatClient(name).register(pwd);
            }
            @Override protected void done() {
                try {
                    String err = get();
                    if (err == null) {
                        JOptionPane.showMessageDialog(LoginFrame.this, "注册成功，请登录", "成功", JOptionPane.INFORMATION_MESSAGE);
                        usernameField.setText(name);
                        usernameField.setForeground(Color.BLACK);
                    } else {
                        JOptionPane.showMessageDialog(LoginFrame.this, err, "注册失败", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(LoginFrame.this, ex.getMessage(), "错误", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
        SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
    }
}

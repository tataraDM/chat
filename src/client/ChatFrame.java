package client;

import common.Message;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class ChatFrame {
    private final ChatClient client;
    private final String peerName;   // 私聊=用户名，群聊=groupId
    private final boolean isGroup;
    private final boolean peerOnline;

    private final Stage stage;
    private VBox messageBox;
    private ScrollPane scrollPane;
    private TextArea inputArea;
    private boolean historyLoaded = false;
    private final List<Message> pendingHistory  = new ArrayList<>();
    private final List<Message> pendingRealtime = new ArrayList<>();

    /** 全部已显示的消息（历史+实时），用于搜索取消后还原 */
    private final List<Message> allMessages = new ArrayList<>();

    // 搜索相关
    private HBox searchBar;
    private TextField searchField;
    private final List<Message> searchResults = new ArrayList<>();
    private boolean searchMode = false;

    // 真实 Emoji（JavaFX 可以正常渲染）
    private static final String[] EMOJIS = {
        "😀", "😂", "😊", "😍", "🥰",
        "😘", "😜", "🤔", "😳", "😢",
        "😭", "😡", "😱", "🙏", "👍",
        "👎", "👌", "❤️", "🔥", "🎉",
        "🌟", "💯", "🚀", "☀️", "🌙",
        "🐶", "🐱", "🌹", "🎂", "☕"
    };

    public ChatFrame(ChatClient client, String peerName, boolean peerOnline, boolean isGroup) {
        this.client     = client;
        this.peerName   = peerName;
        this.peerOnline = peerOnline;
        this.isGroup    = isGroup;

        stage = new Stage();
        stage.setTitle(isGroup ? peerName + " (群聊)" : peerName);
        stage.setWidth(500);
        stage.setHeight(680);

        Scene scene = new Scene(buildUI());
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        stage.setScene(scene);

        // 请求历史记录
        if (isGroup) {
            client.requestGroupHistory(peerName);
        } else {
            client.requestHistory(peerName);
        }
    }

    public Stage getStage() { return stage; }
    public void show() { stage.show(); }

    private BorderPane buildUI() {
        BorderPane root = new BorderPane();

        // === 顶部 ===
        HBox header = new HBox(10);
        header.getStyleClass().add("chat-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label peerLabel = new Label(isGroup ? stage.getTitle() : peerName);
        peerLabel.getStyleClass().add("chat-peer-name");
        Label statusL = new Label(isGroup ? "群聊" : (peerOnline ? "在线" : "离线"));
        statusL.setStyle("-fx-font-size: 11px; -fx-text-fill: " +
                (peerOnline || isGroup ? "#07C160" : "#999999") + ";");
        titleBox.getChildren().addAll(peerLabel, statusL);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button searchBtn = new Button("🔍 搜索");
        searchBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #555; -fx-font-size: 12px; -fx-cursor: hand;");
        searchBtn.setOnAction(e -> toggleSearch());
        header.getChildren().addAll(titleBox, spacer, searchBtn);

        // === 搜索栏（默认隐藏）===
        searchBar = new HBox(6);
        searchBar.getStyleClass().add("search-bar");
        searchBar.setAlignment(Pos.CENTER_LEFT);
        searchBar.setVisible(false);
        searchBar.setManaged(false);

        searchField = new TextField();
        searchField.setPromptText("输入关键字搜索");
        searchField.getStyleClass().add("search-field");
        HBox.setHgrow(searchField, Priority.ALWAYS);
        searchField.setOnAction(e -> doSearch());

        Button doSearchBtn = new Button("搜索");
        doSearchBtn.getStyleClass().add("btn-search");
        doSearchBtn.setOnAction(e -> doSearch());

        Button cancelBtn = new Button("取消");
        cancelBtn.getStyleClass().add("btn-cancel");
        cancelBtn.setOnAction(e -> closeSearchAndRestore());

        searchBar.getChildren().addAll(searchField, doSearchBtn, cancelBtn);

        VBox topArea = new VBox(header, searchBar);

        // === 消息区域 ===
        messageBox = new VBox(4);
        messageBox.getStyleClass().add("chat-area");
        messageBox.setPadding(new Insets(8));

        scrollPane = new ScrollPane(messageBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: #F0F0F0; -fx-background: #F0F0F0;");
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // 自动滚动到底部
        messageBox.heightProperty().addListener((obs, o, n) -> {
            if (!searchMode) scrollPane.setVvalue(1.0);
        });

        // === 输入区 ===
        VBox inputPanel = new VBox(4);
        inputPanel.getStyleClass().add("input-area");

        HBox toolbar = new HBox(8);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Button emojiBtn = new Button("😀 表情");
        emojiBtn.getStyleClass().add("emoji-btn");
        emojiBtn.setOnAction(e -> showEmojiPopup(emojiBtn));

        Label hint = new Label("Enter 发送  |  Shift+Enter 换行");
        hint.setStyle("-fx-text-fill: #AAAAAA; -fx-font-size: 10px;");
        toolbar.getChildren().addAll(emojiBtn, hint);

        inputArea = new TextArea();
        inputArea.setPrefRowCount(3);
        inputArea.setWrapText(true);
        inputArea.setStyle("-fx-font-size: 14px; -fx-background-color: #FAFAFA;");
        inputArea.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) {
                e.consume();
                doSend();
            }
        });

        HBox btnRow = new HBox();
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button sendBtn = new Button("发送");
        sendBtn.getStyleClass().add("send-btn");
        sendBtn.setOnAction(e -> doSend());
        btnRow.getChildren().add(sendBtn);

        inputPanel.getChildren().addAll(toolbar, inputArea, btnRow);

        root.setTop(topArea);
        root.setCenter(scrollPane);
        root.setBottom(inputPanel);
        return root;
    }

    // ===== Emoji 弹出 =====
    private void showEmojiPopup(Button anchor) {
        Popup popup = new Popup();
        popup.setAutoHide(true);

        GridPane grid = new GridPane();
        grid.setHgap(2);
        grid.setVgap(2);
        grid.setPadding(new Insets(8));
        grid.setStyle("-fx-background-color: white; -fx-border-color: #DDDDDD; " +
                "-fx-border-radius: 6; -fx-background-radius: 6; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);");

        int col = 0, row = 0;
        for (String emoji : EMOJIS) {
            Button btn = new Button(emoji);
            btn.getStyleClass().add("emoji-grid-btn");
            btn.setMinSize(40, 36);
            btn.setOnAction(e -> {
                inputArea.insertText(inputArea.getCaretPosition(), emoji);
                popup.hide();
                inputArea.requestFocus();
            });
            grid.add(btn, col, row);
            col++;
            if (col >= 6) { col = 0; row++; }
        }

        popup.getContent().add(grid);
        // 显示在按钮上方
        var bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, bounds.getMinX(), bounds.getMinY() - 220);
    }

    // ===== 搜索 =====
    private void toggleSearch() {
        boolean show = !searchBar.isVisible();
        searchBar.setVisible(show);
        searchBar.setManaged(show);
        if (show) {
            searchField.setText("");
            searchField.requestFocus();
        } else {
            closeSearchAndRestore();
        }
    }

    private void closeSearchAndRestore() {
        searchBar.setVisible(false);
        searchBar.setManaged(false);
        searchMode = false;
        searchResults.clear();
        searchField.setText("");

        // 还原所有聊天记录
        messageBox.getChildren().clear();
        for (Message m : allMessages) {
            addBubbleInternal(m, m.getFrom().equals(client.getUsername()));
        }
        scrollToBottom();
    }

    private void doSearch() {
        String keyword = searchField.getText().trim();
        if (keyword.isEmpty()) return;
        searchMode = true;
        searchResults.clear();
        messageBox.getChildren().clear();

        if (isGroup) {
            client.searchGroupMessages(peerName, keyword);
        } else {
            client.searchMessages(peerName, keyword);
        }
    }

    /** 接收搜索结果 */
    public void receiveSearchResult(Message msg) {
        Platform.runLater(() -> {
            if ("__END__".equals(msg.getFrom())) {
                if (searchResults.isEmpty()) {
                    Label noResult = new Label("没有找到匹配的记录");
                    noResult.setStyle("-fx-text-fill: #999999; -fx-font-size: 13px; -fx-padding: 20;");
                    noResult.setAlignment(Pos.CENTER);
                    noResult.setMaxWidth(Double.MAX_VALUE);
                    messageBox.getChildren().add(noResult);
                } else {
                    Label tip = new Label("搜索到 " + searchResults.size() + " 条记录，点击「取消」返回");
                    tip.setStyle("-fx-text-fill: #8E93A4; -fx-font-size: 11px; -fx-padding: 4 0 8 0;");
                    messageBox.getChildren().add(tip);
                    for (Message r : searchResults) {
                        addBubbleInternal(r, r.getFrom().equals(client.getUsername()));
                    }
                }
                scrollToBottom();
            } else {
                searchResults.add(msg);
            }
        });
    }

    // ===== 发送 =====
    private void doSend() {
        String text = inputArea.getText();
        if (text == null || text.trim().isEmpty()) return;
        // 去掉末尾多余的换行（Enter触发时会多一个\n）
        text = text.stripTrailing();
        if (text.isEmpty()) return;
        inputArea.clear();

        if (searchMode) closeSearchAndRestore();

        if (isGroup) {
            Message msg = new Message(Message.GROUP_CHAT, client.getUsername(), peerName, text);
            client.send(msg);
            addBubble(msg, true);
        } else {
            Message msg = new Message(Message.CHAT, client.getUsername(), peerName, text);
            client.send(msg);
            addBubble(msg, true);
        }
    }

    /** 接收实时消息 */
    public void receiveMessage(Message msg) {
        Platform.runLater(() -> addBubble(msg, msg.getFrom().equals(client.getUsername())));
    }

    /** 接收历史消息 */
    public void receiveHistory(Message msg) {
        Platform.runLater(() -> {
            if ("__END__".equals(msg.getFrom())) {
                historyLoaded = true;
                // 先保存已经在 box 里的实时消息
                var existingNodes = new ArrayList<>(messageBox.getChildren());
                messageBox.getChildren().clear();

                for (Message h : pendingHistory) {
                    addBubbleInternal(h, h.getFrom().equals(client.getUsername()));
                    allMessages.add(h);
                }
                // 恢复之前的实时消息节点
                messageBox.getChildren().addAll(existingNodes);
                pendingHistory.clear();
                for (Message r : pendingRealtime) {
                    addBubbleInternal(r, r.getFrom().equals(client.getUsername()));
                    allMessages.add(r);
                }
                pendingRealtime.clear();
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
        allMessages.add(msg);
        if (searchMode) return; // 搜索模式下不显示新消息，但记录下来
        addBubbleInternal(msg, isMine);
        scrollToBottom();
    }

    private void addBubbleInternal(Message msg, boolean isMine) {
        VBox row = new VBox(2);
        row.setAlignment(Pos.CENTER);
        row.setPadding(new Insets(2, 4, 2, 4));

        // 时间
        Label timeLabel = new Label(msg.getFormattedTime());
        timeLabel.getStyleClass().add("bubble-time");
        timeLabel.setAlignment(Pos.CENTER);
        timeLabel.setMaxWidth(Double.MAX_VALUE);

        // 气泡
        Label bubble = new Label(msg.getContent());
        bubble.setWrapText(true);
        bubble.setMaxWidth(280);
        bubble.getStyleClass().add(isMine ? "bubble-mine" : "bubble-other");
        // 确保 Emoji 字体
        bubble.setStyle(bubble.getStyle() + "; -fx-font-family: 'Segoe UI Emoji', 'Microsoft YaHei';");

        String senderName = isMine ? client.getUsername() : msg.getFrom();
        StackPane avatar = MainFrame.makeAvatar(senderName, 34);

        HBox bubbleRow = new HBox(6);
        bubbleRow.setAlignment(isMine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);

        if (isGroup && !isMine) {
            VBox bubbleWithName = new VBox(1);
            Label senderLabel = new Label(senderName);
            senderLabel.getStyleClass().add("bubble-sender");
            bubbleWithName.getChildren().addAll(senderLabel, bubble);
            bubbleRow.getChildren().addAll(avatar, bubbleWithName);
        } else if (isMine) {
            bubbleRow.getChildren().addAll(bubble, avatar);
        } else {
            bubbleRow.getChildren().addAll(avatar, bubble);
        }

        row.getChildren().addAll(timeLabel, bubbleRow);
        messageBox.getChildren().add(row);
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scrollPane.setVvalue(1.0));
    }
}

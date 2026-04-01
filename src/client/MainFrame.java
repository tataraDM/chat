package client;

import common.Message;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MainFrame implements ChatClient.MessageListener {
    private final ChatClient client;
    private final Stage stage;
    private final Map<String, ChatFrame> openChats = new ConcurrentHashMap<>();
    private final ObservableList<String> contactItems = FXCollections.observableArrayList();
    private final ObservableList<String> groupItems   = FXCollections.observableArrayList();
    private final Set<String> contacts    = new LinkedHashSet<>();
    private final Set<String> onlineUsers = new HashSet<>();
    private final List<String> groupEntries = new ArrayList<>();
    private final Map<String, Integer> unreadCount = new ConcurrentHashMap<>();
    private final List<String> pendingRequests = new ArrayList<>();

    private Label statusLabel;
    private Button notifyBtn;
    private ListView<String> contactListView;
    private ListView<String> groupListView;

    public MainFrame(ChatClient client, Stage loginStage) {
        this.client = client;
        client.setListener(this);
        this.stage = new Stage();
        stage.setTitle("ChaTTE - " + client.getUsername());
        stage.setWidth(340);
        stage.setHeight(640);

        Scene scene = new Scene(buildUI());
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            client.disconnect();
            Platform.exit();
            System.exit(0);
        });
        loginStage.close();
    }

    public void show() { stage.show(); }

    private BorderPane buildUI() {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color: #F5F6FA;");

        // === 顶部 ===
        HBox header = new HBox(10);
        header.getStyleClass().add("main-header");
        header.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = makeAvatar(client.getUsername(), 44);
        VBox nameBox = new VBox(2);
        Label nameLabel = new Label(client.getUsername());
        nameLabel.getStyleClass().add("username-label");
        statusLabel = new Label("在线");
        statusLabel.getStyleClass().add("status-online");
        nameBox.getChildren().addAll(nameLabel, statusLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        notifyBtn = new Button("申请(0)");
        notifyBtn.getStyleClass().add("btn-notify");
        notifyBtn.setVisible(false);
        notifyBtn.setOnAction(e -> showPendingRequestsDialog());

        Button groupBtn = new Button("群+");
        groupBtn.getStyleClass().add("btn-icon");
        groupBtn.setTooltip(new Tooltip("创建群聊"));
        groupBtn.setOnAction(e -> openCreateGroupDialog());

        Button addBtn = new Button("+");
        addBtn.getStyleClass().add("btn-icon");
        addBtn.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        addBtn.setTooltip(new Tooltip("添加联系人"));
        addBtn.setOnAction(e -> openAddContactDialog());

        header.getChildren().addAll(avatar, nameBox, spacer, notifyBtn, groupBtn, addBtn);

        // === 联系人列表 ===
        contactListView = new ListView<>(contactItems);
        contactListView.setCellFactory(lv -> new ContactCell());
        contactListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                String sel = contactListView.getSelectionModel().getSelectedItem();
                if (sel != null) openChat(sel);
            }
        });
        // 右键菜单
        ContextMenu contactMenu = new ContextMenu();
        MenuItem chatItem = new MenuItem("发送消息");
        MenuItem deleteItem = new MenuItem("删除好友");
        deleteItem.setStyle("-fx-text-fill: #E53935;");
        contactMenu.getItems().addAll(chatItem, new SeparatorMenuItem(), deleteItem);
        contactListView.setContextMenu(contactMenu);
        contactMenu.setOnShowing(e -> {
            String sel = contactListView.getSelectionModel().getSelectedItem();
            chatItem.setOnAction(ev -> { if (sel != null) openChat(sel); });
            deleteItem.setOnAction(ev -> {
                if (sel == null) return;
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "确定要删除好友 " + sel + " 吗？", ButtonType.YES, ButtonType.NO);
                confirm.initOwner(stage);
                confirm.setHeaderText("确认删除");
                confirm.showAndWait().ifPresent(bt -> {
                    if (bt == ButtonType.YES) {
                        client.send(new Message(Message.FRIEND_DELETE,
                                client.getUsername(), sel, ""));
                    }
                });
            });
        });

        // === 群聊列表 ===
        groupListView = new ListView<>(groupItems);
        groupListView.setCellFactory(lv -> new GroupCell());
        groupListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 1) {
                String sel = groupListView.getSelectionModel().getSelectedItem();
                if (sel != null) {
                    String[] sp = sel.split(":", 2);
                    openGroupChat(sp[0], sp.length > 1 ? sp[1] : "群聊");
                }
            }
        });

        // === Tab 切换 ===
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        Tab contactTab = new Tab("联系人", contactListView);
        Tab groupTab = new Tab("群聊", groupListView);
        tabs.getTabs().addAll(contactTab, groupTab);

        root.setTop(header);
        root.setCenter(tabs);
        return root;
    }

    // ===== 打开聊天 =====
    private void openChat(String target) {
        unreadCount.remove(target);
        refreshContactList();

        ChatFrame frame = openChats.computeIfAbsent(target, t -> {
            ChatFrame cf = new ChatFrame(client, t, onlineUsers.contains(t), false);
            cf.getStage().setOnHidden(e -> openChats.remove(t));
            cf.show();
            return cf;
        });
        frame.getStage().toFront();
        frame.getStage().requestFocus();
    }

    private void openGroupChat(String groupId, String groupName) {
        String key = "G_" + groupId;
        unreadCount.remove(key);
        refreshGroupList();

        ChatFrame frame = openChats.computeIfAbsent(key, k -> {
            ChatFrame cf = new ChatFrame(client, groupId, true, true);
            cf.getStage().setTitle(groupName + " (群聊)");
            cf.getStage().setOnHidden(e -> openChats.remove(k));
            cf.show();
            return cf;
        });
        frame.getStage().toFront();
        frame.getStage().requestFocus();
    }

    // ===== MessageListener =====
    @Override
    public void onMessage(Message msg) {
        Platform.runLater(() -> dispatch(msg));
    }

    private void dispatch(Message msg) {
        switch (msg.getType()) {
            case Message.USER_LIST:
                onlineUsers.clear();
                if (!msg.getContent().isEmpty())
                    Collections.addAll(onlineUsers, msg.getContent().split(","));
                refreshContactList();
                break;

            case Message.CONTACTS:
                contacts.clear();
                if (!msg.getContent().isEmpty())
                    Collections.addAll(contacts, msg.getContent().split(","));
                refreshContactList();
                break;

            case Message.GROUP_LIST:
                groupEntries.clear();
                if (!msg.getContent().isEmpty())
                    Collections.addAll(groupEntries, msg.getContent().split(","));
                refreshGroupList();
                break;

            case Message.CHAT: {
                String peer = msg.getFrom().equals(client.getUsername())
                        ? msg.getTo() : msg.getFrom();
                ChatFrame cf = openChats.get(peer);
                if (cf != null) cf.receiveMessage(msg);
                else { unreadCount.merge(peer, 1, Integer::sum); refreshContactList(); }
                break;
            }

            case Message.GROUP_CHAT: {
                String groupId = msg.getTo();
                String key = "G_" + groupId;
                ChatFrame cf = openChats.get(key);
                if (cf != null) cf.receiveMessage(msg);
                else { unreadCount.merge(key, 1, Integer::sum); refreshGroupList(); }
                break;
            }

            case Message.GROUP_CREATE_OK:
                showInfo("群聊创建成功！");
                break;

            case Message.HISTORY_RESP: {
                String p = "__END__".equals(msg.getFrom()) ? msg.getTo()
                        : (msg.getFrom().equals(client.getUsername()) ? msg.getTo() : msg.getFrom());
                ChatFrame hcf = openChats.get(p);
                if (hcf != null) hcf.receiveHistory(msg);
                break;
            }

            case Message.GROUP_HISTORY_RESP: {
                String gid = "__END__".equals(msg.getFrom()) ? msg.getTo() : msg.getTo();
                ChatFrame hcf = openChats.get("G_" + gid);
                if (hcf != null) hcf.receiveHistory(msg);
                break;
            }

            case Message.SEARCH_RESP: {
                String peer = "__END__".equals(msg.getFrom()) ? msg.getTo()
                        : (msg.getFrom().equals(client.getUsername()) ? msg.getTo() : msg.getFrom());
                ChatFrame cf = openChats.get(peer);
                if (cf != null) cf.receiveSearchResult(msg);
                break;
            }

            case Message.GROUP_SEARCH_RESP: {
                String gid = "__END__".equals(msg.getFrom()) ? msg.getTo() : msg.getTo();
                ChatFrame gcf = openChats.get("G_" + gid);
                if (gcf != null) gcf.receiveSearchResult(msg);
                break;
            }

            case Message.ALL_USERS_RESP:
                showAddContactDialog(msg.getContent().isEmpty() ? new String[0]
                        : msg.getContent().split(","));
                break;

            case Message.PENDING_REQUESTS:
                pendingRequests.clear();
                if (!msg.getContent().isEmpty())
                    Collections.addAll(pendingRequests, msg.getContent().split(","));
                updateNotifyBtn();
                break;

            case Message.FRIEND_REQ:
                if (!pendingRequests.contains(msg.getFrom()))
                    pendingRequests.add(msg.getFrom());
                updateNotifyBtn();
                break;

            case Message.FRIEND_ACCEPT:
                if ("accept".equals(msg.getContent()))
                    showInfo(msg.getFrom() + " 接受了你的好友请求");
                break;

            case Message.FRIEND_REJECT:
                showInfo(msg.getContent());
                break;

            default: break;
        }
    }

    @Override
    public void onDisconnected() {
        Platform.runLater(() -> {
            statusLabel.setText("已断开");
            statusLabel.getStyleClass().setAll("status-offline");
        });
    }

    // ===== 刷新列表 =====
    private void refreshContactList() {
        contactItems.setAll(contacts);
    }

    private void refreshGroupList() {
        groupItems.setAll(groupEntries);
    }

    private void updateNotifyBtn() {
        int n = pendingRequests.size();
        notifyBtn.setText("申请(" + n + ")");
        notifyBtn.setVisible(n > 0);
    }

    // ===== 对话框 =====
    private void showPendingRequestsDialog() {
        if (pendingRequests.isEmpty()) return;
        Stage dlg = new Stage();
        dlg.setTitle("好友申请");
        dlg.initOwner(stage);
        dlg.setWidth(320);
        dlg.setHeight(400);

        VBox list = new VBox(2);
        list.setPadding(new Insets(8));

        List<String> snapshot = new ArrayList<>(pendingRequests);
        for (String req : snapshot) {
            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(8, 14, 8, 14));
            row.setStyle("-fx-background-color: white; -fx-border-color: transparent transparent #EEF0FA transparent;");

            StackPane av = makeAvatar(req, 36);
            Label nameL = new Label(req);
            nameL.setStyle("-fx-font-size: 13px;");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);

            Button accBtn = new Button("同意");
            accBtn.getStyleClass().add("btn-icon");
            accBtn.setOnAction(e -> {
                client.send(new Message(Message.FRIEND_ACCEPT, client.getUsername(), req, "accept"));
                pendingRequests.remove(req);
                updateNotifyBtn();
                dlg.close();
                if (!pendingRequests.isEmpty()) showPendingRequestsDialog();
            });

            Button rejBtn = new Button("拒绝");
            rejBtn.setStyle("-fx-background-color: #AAAAAA; -fx-text-fill: white; -fx-background-radius: 4; -fx-cursor: hand; -fx-padding: 4 8;");
            rejBtn.setOnAction(e -> {
                client.send(new Message(Message.FRIEND_ACCEPT, client.getUsername(), req, "reject"));
                pendingRequests.remove(req);
                updateNotifyBtn();
                dlg.close();
                if (!pendingRequests.isEmpty()) showPendingRequestsDialog();
            });

            row.getChildren().addAll(av, nameL, sp, accBtn, rejBtn);
            list.getChildren().add(row);
        }

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        Label title = new Label("待处理的好友申请");
        title.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-padding: 12 0 8 0;");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);

        VBox layout = new VBox(title, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        dlg.setScene(new Scene(layout));
        dlg.getScene().getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        dlg.show();
    }

    private void openAddContactDialog() {
        client.send(new Message(Message.ALL_USERS_REQ, client.getUsername(), "server", ""));
    }

    private void showAddContactDialog(String[] allUsers) {
        List<String> candidates = new ArrayList<>();
        for (String u : allUsers) {
            if (!contacts.contains(u) && !u.equals(client.getUsername()))
                candidates.add(u);
        }

        Stage dlg = new Stage();
        dlg.setTitle("添加联系人");
        dlg.initOwner(stage);
        dlg.setWidth(300);
        dlg.setHeight(420);

        Label hint = new Label("选择要添加的用户");
        hint.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-padding: 12;");
        hint.setAlignment(Pos.CENTER);
        hint.setMaxWidth(Double.MAX_VALUE);

        if (candidates.isEmpty()) {
            VBox layout = new VBox(hint, new Label("暂无可添加的用户"));
            layout.setAlignment(Pos.CENTER);
            dlg.setScene(new Scene(layout));
            dlg.show();
            return;
        }

        ListView<String> listView = new ListView<>(FXCollections.observableArrayList(candidates));
        listView.setCellFactory(lv -> new ContactCell());

        Button sendBtn = new Button("发送好友请求");
        sendBtn.getStyleClass().add("btn-primary");
        sendBtn.setMaxWidth(Double.MAX_VALUE);
        sendBtn.setOnAction(e -> {
            String target = listView.getSelectionModel().getSelectedItem();
            if (target == null) { showInfo("请先选择一个用户"); return; }
            client.send(new Message(Message.FRIEND_REQ, client.getUsername(), target, ""));
            dlg.close();
        });

        VBox layout = new VBox(8, hint, listView, sendBtn);
        layout.setPadding(new Insets(0, 8, 8, 8));
        VBox.setVgrow(listView, Priority.ALWAYS);
        Scene scene = new Scene(layout);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        dlg.setScene(scene);
        dlg.show();
    }

    private void openCreateGroupDialog() {
        if (contacts.isEmpty()) {
            showInfo("你还没有联系人，无法创建群聊");
            return;
        }

        Stage dlg = new Stage();
        dlg.setTitle("创建群聊");
        dlg.initOwner(stage);
        dlg.setWidth(320);
        dlg.setHeight(450);

        Label nameHint = new Label("群聊名称：");
        nameHint.setStyle("-fx-font-size: 13px;");
        TextField nameField = new TextField();
        nameField.setPromptText("输入群聊名称");
        HBox nameRow = new HBox(8, nameHint, nameField);
        nameRow.setAlignment(Pos.CENTER_LEFT);
        nameRow.setPadding(new Insets(12, 14, 4, 14));
        HBox.setHgrow(nameField, Priority.ALWAYS);

        Label selectHint = new Label("选择群成员（可多选，Ctrl+点击）：");
        selectHint.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888; -fx-padding: 4 14;");

        ListView<String> memberList = new ListView<>(FXCollections.observableArrayList(contacts));
        memberList.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        memberList.setStyle("-fx-font-size: 13px;");

        Button createBtn = new Button("创建群聊");
        createBtn.getStyleClass().add("btn-primary");
        createBtn.setMaxWidth(Double.MAX_VALUE);
        createBtn.setOnAction(e -> {
            String gName = nameField.getText().trim();
            if (gName.isEmpty()) { showInfo("请输入群聊名称"); return; }
            List<String> selected = memberList.getSelectionModel().getSelectedItems();
            if (selected.isEmpty()) { showInfo("请至少选择一个群成员"); return; }
            StringBuilder content = new StringBuilder(gName);
            for (String m : selected) content.append(",").append(m);
            client.send(new Message(Message.GROUP_CREATE, client.getUsername(), "server", content.toString()));
            dlg.close();
        });

        VBox layout = new VBox(4, nameRow, selectHint, memberList, createBtn);
        layout.setPadding(new Insets(0, 8, 8, 8));
        VBox.setVgrow(memberList, Priority.ALWAYS);
        Scene scene = new Scene(layout);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        dlg.setScene(scene);
        dlg.show();
    }

    // ===== 工具方法 =====
    static StackPane makeAvatar(String name, int size) {
        StackPane stack = new StackPane();
        stack.setMinSize(size, size);
        stack.setMaxSize(size, size);

        int hash = name.hashCode();
        float hue = Math.abs(hash % 360);
        Color color = Color.hsb(hue, 0.55, 0.85);
        String hex = String.format("#%02X%02X%02X",
                (int)(color.getRed()*255), (int)(color.getGreen()*255), (int)(color.getBlue()*255));

        Circle circle = new Circle(size / 2.0);
        circle.setFill(color);

        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Label letter = new Label(initial);
        letter.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: " + (size/2 - 2) + "px;");

        stack.getChildren().addAll(circle, letter);
        return stack;
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.initOwner(stage);
        a.setHeaderText(null);
        a.showAndWait();
    }

    // ===== 联系人 Cell =====
    private class ContactCell extends ListCell<String> {
        @Override
        protected void updateItem(String name, boolean empty) {
            super.updateItem(name, empty);
            if (empty || name == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));

            StackPane av = makeAvatar(name, 40);

            VBox info = new VBox(2);
            Label nameL = new Label(name);
            nameL.getStyleClass().add("contact-name");
            boolean online = onlineUsers.contains(name);
            Label statusL = new Label(online ? "● 在线" : "○ 离线");
            statusL.getStyleClass().add(online ? "contact-online" : "contact-offline-text");
            info.getChildren().addAll(nameL, statusL);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(av, info, spacer);

            // 未读红点
            int count = unreadCount.getOrDefault(name, 0);
            if (count > 0) {
                Label badge = new Label(count > 99 ? "99+" : String.valueOf(count));
                badge.getStyleClass().add("badge");
                row.getChildren().add(badge);
            }

            setGraphic(row);
            setText(null);
        }
    }

    // ===== 群聊 Cell =====
    private class GroupCell extends ListCell<String> {
        @Override
        protected void updateItem(String value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || value == null) {
                setGraphic(null);
                setText(null);
                return;
            }

            String[] sp = value.split(":", 2);
            String groupId = sp[0];
            String groupName = sp.length > 1 ? sp[1] : "群聊";

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));

            StackPane icon = new StackPane();
            icon.setMinSize(40, 40);
            icon.setMaxSize(40, 40);
            icon.setStyle("-fx-background-color: #7c6cd4; -fx-background-radius: 8;");
            Label gl = new Label("群");
            gl.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 16px;");
            icon.getChildren().add(gl);

            Label nameL = new Label(groupName);
            nameL.getStyleClass().add("contact-name");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            row.getChildren().addAll(icon, nameL, spacer);

            int count = unreadCount.getOrDefault("G_" + groupId, 0);
            if (count > 0) {
                Label badge = new Label(count > 99 ? "99+" : String.valueOf(count));
                badge.getStyleClass().add("badge");
                row.getChildren().add(badge);
            }

            setGraphic(row);
            setText(null);
        }
    }
}

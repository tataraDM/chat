package client;

import common.Message;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class LoginFrame extends Application {
    private TextField usernameField;
    private PasswordField passwordField;
    private Button loginBtn;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;
        stage.setTitle("ChaTTE - 登录");
        stage.setResizable(false);

        Scene scene = new Scene(buildUI(), 380, 540);
        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private VBox buildUI() {
        VBox root = new VBox();
        root.getStyleClass().add("login-root");
        root.setAlignment(Pos.TOP_CENTER);

        // === 顶部 Logo ===
        VBox topBox = new VBox(12);
        topBox.setAlignment(Pos.CENTER);
        topBox.setPadding(new Insets(55, 0, 35, 0));

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("login-avatar");
        Label avatarLetter = new Label("C");
        avatarLetter.setStyle("-fx-text-fill: #667eea; -fx-font-size: 36px; -fx-font-weight: bold;");
        avatar.getChildren().add(avatarLetter);

        Label title = new Label("ChaTTE");
        title.getStyleClass().add("login-title");
        Label subtitle = new Label("即时通信应用");
        subtitle.getStyleClass().add("login-subtitle");

        topBox.getChildren().addAll(avatar, title, subtitle);

        // === 表单卡片 ===
        VBox formCard = new VBox(14);
        formCard.getStyleClass().add("form-card");

        usernameField = new TextField();
        usernameField.setPromptText("用户名");
        usernameField.getStyleClass().add("form-field");
        usernameField.setOnAction(e -> doLogin());

        passwordField = new PasswordField();
        passwordField.setPromptText("密码");
        passwordField.getStyleClass().add("form-field");
        passwordField.setOnAction(e -> doLogin());

        loginBtn = new Button("登 录");
        loginBtn.getStyleClass().add("btn-primary");
        loginBtn.setMaxWidth(Double.MAX_VALUE);
        loginBtn.setOnAction(e -> doLogin());

        // 注册行
        HBox regRow = new HBox(4);
        regRow.setAlignment(Pos.CENTER);
        Label regHint = new Label("还没有账号？");
        regHint.setStyle("-fx-text-fill: #8E93A4; -fx-font-size: 12px;");
        Button regBtn = new Button("立即注册");
        regBtn.getStyleClass().add("link-btn");
        regBtn.setOnAction(e -> doRegister());
        regRow.getChildren().addAll(regHint, regBtn);

        formCard.getChildren().addAll(usernameField, passwordField, loginBtn, regRow);

        VBox formWrapper = new VBox(formCard);
        formWrapper.setPadding(new Insets(0, 24, 36, 24));

        VBox.setVgrow(topBox, Priority.ALWAYS);
        root.getChildren().addAll(topBox, formWrapper);
        return root;
    }

    private void doLogin() {
        String name = usernameField.getText().trim();
        String pwd = passwordField.getText();
        if (name.isEmpty()) { showAlert("请输入用户名"); return; }
        if (pwd.isEmpty()) { showAlert("请输入密码"); return; }

        loginBtn.setDisable(true);
        loginBtn.setText("连接中...");

        new Thread(() -> {
            ChatClient client = new ChatClient(name);
            String err = client.connect(pwd);
            Platform.runLater(() -> {
                if (err == null) {
                    new MainFrame(client, primaryStage).show();
                } else {
                    showAlert(err);
                    loginBtn.setDisable(false);
                    loginBtn.setText("登 录");
                }
            });
        }).start();
    }

    private void doRegister() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("注册新账号");
        dialog.initOwner(primaryStage);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(16));

        TextField regName = new TextField();
        regName.setPromptText("用户名");
        PasswordField regPwd = new PasswordField();
        regPwd.setPromptText("密码");
        PasswordField regPwd2 = new PasswordField();
        regPwd2.setPromptText("确认密码");

        grid.add(new Label("用户名:"), 0, 0);   grid.add(regName, 1, 0);
        grid.add(new Label("密码:"), 0, 1);     grid.add(regPwd, 1, 1);
        grid.add(new Label("确认密码:"), 0, 2); grid.add(regPwd2, 1, 2);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt != ButtonType.OK) return;
            String name = regName.getText().trim();
            String pwd = regPwd.getText();
            String pwd2 = regPwd2.getText();
            if (name.isEmpty()) { showAlert("用户名不能为空"); return; }
            if (pwd.isEmpty()) { showAlert("密码不能为空"); return; }
            if (!pwd.equals(pwd2)) { showAlert("两次密码不一致"); return; }

            new Thread(() -> {
                String err = new ChatClient(name).register(pwd);
                Platform.runLater(() -> {
                    if (err == null) {
                        showInfo("注册成功，请登录");
                        usernameField.setText(name);
                    } else {
                        showAlert(err);
                    }
                });
            }).start();
        });
    }

    private void showAlert(String msg) {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.initOwner(primaryStage);
        a.setHeaderText(null);
        a.showAndWait();
    }

    private void showInfo(String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION, msg, ButtonType.OK);
        a.initOwner(primaryStage);
        a.setHeaderText(null);
        a.showAndWait();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

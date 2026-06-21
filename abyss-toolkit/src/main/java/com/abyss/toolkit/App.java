package com.abyss.toolkit;

import com.abyss.toolkit.db.DatabaseManager;
import com.abyss.toolkit.ui.CoreViews;
import com.abyss.toolkit.ui.ToolViews;
import com.abyss.toolkit.ui.AttackViews;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class App extends Application {

    public interface Module {
        String getName();
        String getDescription();
        Node getView();
    }

    private final StackPane contentArea = new StackPane();
    private final List<Button> navButtons = new ArrayList<>();
    private Scene scene;
    private BorderPane root;

    @Override
    public void init() {
        DatabaseManager.init();
        ThemeManager.loadTheme();
    }

    @Override
    public void start(Stage stage) {
        root = new BorderPane();
        root.setLeft(buildSidebar());
        contentArea.setPadding(new Insets(15, 20, 20, 20));
        ScrollPane scrollCenter = new ScrollPane(contentArea);
        scrollCenter.setFitToWidth(true);
        scrollCenter.setFitToHeight(true);
        scrollCenter.setStyle("-fx-background: transparent; -fx-background-color: transparent; -fx-border: none;");
        root.setCenter(scrollCenter);

        scene = new Scene(root, Screen.getPrimary().getVisualBounds().getWidth(),
                                   Screen.getPrimary().getVisualBounds().getHeight());
        applyTheme(ThemeManager.getCurrentTheme());

        stage.setTitle("Abyss Toolkit v4.0");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.show();

        List<Module> modules = registerModules();
        if (!modules.isEmpty()) {
            contentArea.getChildren().setAll(modules.get(0).getView());
            selectNav(0);
        }
    }

    private VBox buildSidebar() {
        VBox sidebar = new VBox();
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(220);
        sidebar.setPadding(new Insets(0, 0, 20, 0));

        Label brand = new Label("✦ ABYSS");
        brand.getStyleClass().add("brand-label");
        Label sub = new Label("SECURITY TOOLKIT v4.0");
        sub.getStyleClass().add("brand-sub-label");
        VBox brandBox = new VBox(0, brand, sub);
        brandBox.setPadding(new Insets(22, 18, 28, 18));

        List<Module> modules = registerModules();
        VBox navBox = new VBox(4);
        for (int i = 0; i < modules.size(); i++) {
            int index = i;
            Module m = modules.get(i);
            Button btn = new Button(m.getName());
            btn.getStyleClass().add("nav-button");
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.setOnAction(e -> {
                selectNav(index);
                contentArea.getChildren().setAll(m.getView());
            });
            navButtons.add(btn);
            navBox.getChildren().add(btn);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button themeToggle = new Button("◉ Toggle Theme");
        themeToggle.getStyleClass().add("nav-button");
        themeToggle.setMaxWidth(Double.MAX_VALUE);
        themeToggle.setAlignment(Pos.CENTER_LEFT);
        themeToggle.setOnAction(e -> {
            ThemeManager.toggleTheme();
            applyTheme(ThemeManager.getCurrentTheme());
        });

        sidebar.getChildren().addAll(brandBox, navBox, spacer, themeToggle);
        return sidebar;
    }

    private List<Module> registerModules() {
        List<Module> modules = new ArrayList<>();
        modules.add(simpleModule("Dashboard", "Overview & statistics", CoreViews.DashboardView::new));
        modules.add(simpleModule("Network", "Port scanning, DNS, host discovery", CoreViews.NetworkView::new));
        modules.add(simpleModule("Web Security", "Headers, HTTPS, SSL/TLS", CoreViews.WebSecurityView::new));
        modules.add(simpleModule("OSINT", "Whois, GeoIP, DNS enum", ToolViews.OsintView::new));
        modules.add(simpleModule("Utilities", "Hash, Encode, JWT", ToolViews.UtilitiesView::new));
        modules.add(simpleModule("Logs", "Apache, SSH, generic", ToolViews.LogsView::new));
        modules.add(simpleModule("Reports", "PDF, HTML, Excel, CSV", ToolViews.ReportsView::new));
        modules.add(simpleModule("⚡ Attack", "Dirbuster, SQLi, Subdomain", AttackViews.AttackView::new));
        modules.add(simpleModule("Settings", "Preferences", () -> new ToolViews.SettingsView(this::applyTheme)));
        return modules;
    }

    private Module simpleModule(String name, String description, java.util.function.Supplier<Node> viewFactory) {
        return new Module() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return description; }
            @Override public Node getView() { return viewFactory.get(); }
        };
    }

    private void selectNav(int index) {
        for (int i = 0; i < navButtons.size(); i++) {
            navButtons.get(i).getStyleClass().remove("nav-button-active");
            if (i == index) navButtons.get(i).getStyleClass().add("nav-button-active");
        }
    }

    private void applyTheme(String theme) {
        String cssFile = "light".equalsIgnoreCase(theme) ? "/theme-light.css" : "/theme-dark.css";
        scene.getStylesheets().clear();
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource(cssFile)).toExternalForm());
        ThemeManager.setCurrentTheme(theme);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

class ThemeManager {
    private static String currentTheme = "dark";
    static void loadTheme() { currentTheme = DatabaseManager.getSetting("theme", "dark"); }
    static String getCurrentTheme() { return currentTheme; }
    static void setCurrentTheme(String theme) { currentTheme = theme; DatabaseManager.saveSetting("theme", theme); }
    static void toggleTheme() { currentTheme = "dark".equals(currentTheme) ? "light" : "dark"; DatabaseManager.saveSetting("theme", currentTheme); }
}
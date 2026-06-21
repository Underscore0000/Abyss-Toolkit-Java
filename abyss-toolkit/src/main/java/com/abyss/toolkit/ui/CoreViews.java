package com.abyss.toolkit.ui;

import com.abyss.toolkit.db.DatabaseManager;
import com.abyss.toolkit.service.ScanServices;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class CoreViews {

    public static class DashboardView extends VBox {
        private TableView<Map<String, String>> table;

        public DashboardView() {
            setSpacing(18);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");

            Label title = new Label("Dashboard");
            title.setStyle("-fx-font-size: 26px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;");

            int totalScans = DatabaseManager.countScans();
            int totalReports = DatabaseManager.countReports();
            long uniqueHosts = DatabaseManager.recentScans(1000).stream().map(m -> m.get("target")).distinct().count();
            long attackCount = DatabaseManager.recentScans(1000).stream().filter(m -> "attack".equals(m.get("type"))).count();

            HBox statRow = new HBox(16);
            statRow.getChildren().addAll(
                    statCard("Scans", String.valueOf(totalScans)),
                    statCard("Reports", String.valueOf(totalReports)),
                    statCard("Hosts", String.valueOf(uniqueHosts)),
                    statCard("Attacks", String.valueOf(attackCount))
            );

            Label recentLabel = new Label("Recent Activity");
            recentLabel.setStyle("-fx-text-fill: #E4E4E7; -fx-font-size: 16px; -fx-font-weight: 700; -fx-padding: 10 0 5 0;");

            table = new TableView<>();
            TableColumn<Map<String, String>, String> colId = new TableColumn<>("ID");
            colId.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().get("id")));
            colId.setPrefWidth(50);
            TableColumn<Map<String, String>, String> colType = new TableColumn<>("Type");
            colType.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().get("type")));
            colType.setPrefWidth(120);
            TableColumn<Map<String, String>, String> colTarget = new TableColumn<>("Target");
            colTarget.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().get("target")));
            colTarget.setPrefWidth(200);
            TableColumn<Map<String, String>, String> colResult = new TableColumn<>("Result");
            colResult.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().get("result")));
            colResult.setPrefWidth(250);
            TableColumn<Map<String, String>, String> colTime = new TableColumn<>("Timestamp");
            colTime.setCellValueFactory(p -> new javafx.beans.property.SimpleStringProperty(p.getValue().get("timestamp")));
            colTime.setPrefWidth(200);
            table.getColumns().addAll(colId, colType, colTarget, colResult, colTime);
            table.setPrefHeight(320);
            table.setPlaceholder(new Label("No scan history yet."));

            Button deleteBtn = new Button("Delete Selected");
            deleteBtn.getStyleClass().add("button-danger");
            deleteBtn.setOnAction(e -> deleteSelected());

            Button clearAllBtn = new Button("Clear All");
            clearAllBtn.getStyleClass().add("button-danger");
            clearAllBtn.setOnAction(e -> clearAll());

            Button exportBtn = new Button("Export CSV");
            exportBtn.getStyleClass().add("button-primary");
            exportBtn.setOnAction(e -> exportCSV());

            HBox toolbar = new HBox(12, deleteBtn, clearAllBtn, exportBtn);
            toolbar.setAlignment(Pos.CENTER_LEFT);

            refreshTable();

            getChildren().addAll(title, statRow, recentLabel, toolbar, table);
        }

        private void refreshTable() {
            table.getItems().clear();
            table.getItems().addAll(DatabaseManager.recentScans(100));
        }

        private void deleteSelected() {
            var selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) return;
            String idStr = selected.get("id");
            if (idStr == null) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Delete Entry");
            confirm.setHeaderText("Delete this scan entry?");
            confirm.setContentText("This action is permanent and cannot be undone.");
            confirm.initOwner(table.getScene().getWindow());

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                int id = Integer.parseInt(idStr);
                DatabaseManager.deleteScan(id);
                refreshTable();
            }
        }

        private void clearAll() {
            if (table.getItems().isEmpty()) return;

            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Clear All Scans");
            confirm.setHeaderText("Delete ALL scan history?");
            confirm.setContentText("This will permanently delete all scan records.");
            confirm.initOwner(table.getScene().getWindow());

            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                DatabaseManager.deleteAllScans();
                refreshTable();
            }
        }

        private void exportCSV() {
            var items = table.getItems();
            if (items.isEmpty()) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export");
                alert.setHeaderText("No data to export");
                alert.showAndWait();
                return;
            }

            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV", "*.csv"));
            fc.setInitialFileName("abyss_scans_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
            var file = fc.showSaveDialog(table.getScene().getWindow());

            if (file != null) {
                try {
                    StringBuilder csv = new StringBuilder();
                    csv.append("ID,Type,Target,Result,Timestamp\n");
                    for (var row : items) {
                        csv.append(row.get("id")).append(",")
                           .append(row.get("type")).append(",")
                           .append(row.get("target")).append(",")
                           .append(row.get("result")).append(",")
                           .append(row.get("timestamp")).append("\n");
                    }
                    java.nio.file.Files.writeString(file.toPath(), csv.toString());
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Export");
                    alert.setHeaderText("Export successful");
                    alert.setContentText("File saved to: " + file.getAbsolutePath());
                    alert.showAndWait();
                } catch (Exception e) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Export Error");
                    alert.setHeaderText("Failed to export");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                }
            }
        }

        private VBox statCard(String label, String value) {
            VBox card = new VBox(4);
            card.getStyleClass().add("stat-card");
            card.setPadding(new Insets(16, 20, 16, 20));
            card.setMinWidth(150);
            Label valLbl = new Label(value);
            valLbl.getStyleClass().add("stat-value");
            Label lblLbl = new Label(label);
            lblLbl.getStyleClass().add("stat-label");
            card.getChildren().addAll(valLbl, lblLbl);
            return card;
        }
    }

    public static class NetworkView extends VBox {
        public NetworkView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");
            getChildren().addAll(
                    new Label("Network Tools") {{
                        setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;");
                    }},
                    buildTabs()
            );
        }

        private TabPane buildTabs() {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getTabs().addAll(
                    new Tab("Port Scanner", portScannerPane()),
                    new Tab("Host Discovery", hostDiscoveryPane()),
                    new Tab("DNS Tools", dnsPane())
            );
            return tabs;
        }

        private Node portScannerPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));

            GridPane config = new GridPane();
            config.setHgap(12);
            config.setVgap(10);
            config.setPadding(new Insets(10, 0, 10, 0));

            TextField host = new TextField("scanme.nmap.org");
            host.setPromptText("Target host or IP");
            TextField startPort = new TextField("1");
            startPort.setPrefWidth(60);
            TextField endPort = new TextField("1024");
            endPort.setPrefWidth(60);
            Spinner<Integer> timeout = new Spinner<>(100, 10000, 800, 100);
            timeout.setPrefWidth(80);
            Spinner<Integer> threads = new Spinner<>(1, 500, 100);
            threads.setPrefWidth(80);
            ComboBox<String> scanType = new ComboBox<>();
            scanType.getItems().addAll("Quick Scan", "Custom Range", "Full Scan");
            scanType.setValue("Quick Scan");

            config.addRow(0, new Label("Target:"), host);
            config.addRow(1, new Label("Scan Type:"), scanType);
            config.addRow(2, new Label("Port Range:"), new HBox(5, startPort, new Label("-"), endPort));
            config.addRow(3, new Label("Timeout:"), timeout, new Label("Threads:"), threads);

            Button runBtn = new Button("Start Scan");
            runBtn.getStyleClass().add("button-primary");
            Button stopBtn = new Button("Stop");
            stopBtn.getStyleClass().add("button-danger");
            stopBtn.setDisable(true);

            HBox actionBar = new HBox(12, runBtn, stopBtn);

            ProgressBar progress = new ProgressBar(0);
            progress.setPrefWidth(Double.MAX_VALUE);
            Label progressLabel = new Label("Ready");
            progressLabel.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 12px;");

            TableView<ScanServices.PortScanner.PortResult> table = new TableView<>();
            TableColumn<ScanServices.PortScanner.PortResult, Integer> colPort = new TableColumn<>("Port");
            colPort.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().port()));
            colPort.setPrefWidth(70);
            TableColumn<ScanServices.PortScanner.PortResult, String> colService = new TableColumn<>("Service");
            colService.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().service()));
            colService.setPrefWidth(150);
            TableColumn<ScanServices.PortScanner.PortResult, String> colBanner = new TableColumn<>("Banner");
            colBanner.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().banner()));
            colBanner.setPrefWidth(280);
            TableColumn<ScanServices.PortScanner.PortResult, Long> colLat = new TableColumn<>("Latency");
            colLat.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().latencyMs()));
            colLat.setPrefWidth(90);
            table.getColumns().addAll(colPort, colService, colBanner, colLat);
            table.setPrefHeight(280);
            table.setPlaceholder(new Label("No results yet. Run a scan."));

            Label statsLabel = new Label("Open ports: 0 | Scanned: 0 | Duration: -");
            statsLabel.setStyle("-fx-text-fill: #6A6A7A; -fx-font-size: 12px;");

            VBox resultsBox = new VBox(5, table, statsLabel);

            AtomicLong startTime = new AtomicLong(0);
            AtomicBoolean running = new AtomicBoolean(false);
            AtomicBoolean stopped = new AtomicBoolean(false);

            runBtn.setOnAction(e -> {
                table.getItems().clear();
                progress.setProgress(0);
                progressLabel.setText("Initializing...");
                statsLabel.setText("Open ports: 0 | Scanned: 0 | Duration: -");
                startTime.set(System.currentTimeMillis());
                running.set(true);
                stopped.set(false);
                runBtn.setDisable(true);
                stopBtn.setDisable(false);

                String target = host.getText().trim();
                String scanTypeVal = scanType.getValue();
                int timeoutMs = timeout.getValue();
                int threadCount = threads.getValue();

                int sPort = 1;
                int ePort = 1024;
                boolean isQuick = false;

                if ("Quick Scan".equals(scanTypeVal)) {
                    isQuick = true;
                } else if ("Full Scan".equals(scanTypeVal)) {
                    sPort = 1;
                    ePort = 65535;
                } else {
                    try { sPort = Integer.parseInt(startPort.getText().trim()); } catch (Exception ex) {}
                    try { ePort = Integer.parseInt(endPort.getText().trim()); } catch (Exception ex) {}
                }

                final int finalSPort = sPort;
                final int finalEPort = ePort;
                final String finalTarget = target;
                final boolean finalIsQuick = isQuick;

                Task<List<ScanServices.PortScanner.PortResult>> task = new Task<>() {
                    @Override protected List<ScanServices.PortScanner.PortResult> call() {
                        if (finalIsQuick) {
                            return ScanServices.PortScanner.quickScan(finalTarget, pct -> {
                                Platform.runLater(() -> {
                                    progress.setProgress(pct / 100.0);
                                    progressLabel.setText(String.format("Scanning... %d%%", pct));
                                });
                            });
                        } else {
                            return ScanServices.PortScanner.scan(finalTarget, finalSPort, finalEPort,
                                    timeoutMs, threadCount, pct -> {
                                        Platform.runLater(() -> {
                                            progress.setProgress(pct / 100.0);
                                            progressLabel.setText(String.format("Scanning... %d%%", pct));
                                        });
                                    });
                        }
                    }
                };

                task.setOnSucceeded(ev -> {
                    running.set(false);
                    var results = task.getValue();
                    table.getItems().addAll(results);
                    long open = results.stream().filter(r -> r.open()).count();
                    long scanned = results.size();
                    long elapsed = System.currentTimeMillis() - startTime.get();

                    statsLabel.setText(String.format("Open ports: %d | Scanned: %d | Duration: %.2f sec",
                            open, scanned, elapsed / 1000.0));
                    progressLabel.setText("Scan completed");
                    progress.setProgress(1.0);

                    DatabaseManager.recordScan("network", finalTarget,
                            open + " open ports (" + scanned + " scanned)", "");
                    runBtn.setDisable(false);
                    stopBtn.setDisable(true);
                });

                task.setOnFailed(ev -> {
                    running.set(false);
                    progressLabel.setText("Scan failed: " + task.getException().getMessage());
                    runBtn.setDisable(false);
                    stopBtn.setDisable(true);
                });

                new Thread(task, "port-scan").start();
            });

            stopBtn.setOnAction(e -> {
                stopped.set(true);
                running.set(false);
                progressLabel.setText("Stopped by user");
                runBtn.setDisable(false);
                stopBtn.setDisable(true);
            });

            box.getChildren().addAll(config, actionBar, progress, progressLabel, resultsBox);
            return box;
        }

        private Node hostDiscoveryPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));

            GridPane config = new GridPane();
            config.setHgap(12);
            config.setVgap(10);
            config.setPadding(new Insets(10, 0, 10, 0));

            TextField cidr = new TextField("192.168.1.0/24");
            cidr.setPromptText("CIDR notation");
            Spinner<Integer> timeout = new Spinner<>(100, 5000, 600, 100);
            timeout.setPrefWidth(80);
            Spinner<Integer> threads = new Spinner<>(1, 200, 64);
            threads.setPrefWidth(80);

            config.addRow(0, new Label("CIDR:"), cidr);
            config.addRow(1, new Label("Timeout:"), timeout, new Label("Threads:"), threads);

            Button runBtn = new Button("Discover Hosts");
            runBtn.getStyleClass().add("button-primary");
            Button stopBtn = new Button("Stop");
            stopBtn.getStyleClass().add("button-danger");
            stopBtn.setDisable(true);

            ProgressBar progress = new ProgressBar(0);
            progress.setPrefWidth(Double.MAX_VALUE);
            Label progressLabel = new Label("Ready");
            progressLabel.setStyle("-fx-text-fill: #8A8A9A; -fx-font-size: 12px;");

            TableView<ScanServices.HostDiscovery.HostStatus> table = new TableView<>();
            TableColumn<ScanServices.HostDiscovery.HostStatus, String> colIP = new TableColumn<>("IP");
            colIP.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().ip()));
            colIP.setPrefWidth(140);
            TableColumn<ScanServices.HostDiscovery.HostStatus, String> colHost = new TableColumn<>("Hostname");
            colHost.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().hostname()));
            colHost.setPrefWidth(200);
            TableColumn<ScanServices.HostDiscovery.HostStatus, Long> colLat = new TableColumn<>("Latency");
            colLat.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().latencyMs()));
            colLat.setPrefWidth(100);
            TableColumn<ScanServices.HostDiscovery.HostStatus, String> colMAC = new TableColumn<>("MAC");
            colMAC.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().mac()));
            colMAC.setPrefWidth(180);
            table.getColumns().addAll(colIP, colHost, colLat, colMAC);
            table.setPrefHeight(300);
            table.setPlaceholder(new Label("No results yet. Run a scan."));

            Label statsLabel = new Label("Hosts online: 0 | Duration: -");
            statsLabel.setStyle("-fx-text-fill: #6A6A7A; -fx-font-size: 12px;");

            AtomicLong startTime = new AtomicLong(0);
            AtomicBoolean running = new AtomicBoolean(false);
            AtomicBoolean stopped = new AtomicBoolean(false);

            runBtn.setOnAction(e -> {
                table.getItems().clear();
                progress.setProgress(0);
                progressLabel.setText("Initializing...");
                statsLabel.setText("Hosts online: 0 | Duration: -");
                startTime.set(System.currentTimeMillis());
                running.set(true);
                stopped.set(false);
                runBtn.setDisable(true);
                stopBtn.setDisable(false);

                String cidrVal = cidr.getText().trim();
                int timeoutMs = timeout.getValue();

                Task<List<ScanServices.HostDiscovery.HostStatus>> task = new Task<>() {
                    @Override protected List<ScanServices.HostDiscovery.HostStatus> call() {
                        return ScanServices.HostDiscovery.discover(cidrVal, timeoutMs, pct -> {
                            Platform.runLater(() -> {
                                progress.setProgress(pct / 100.0);
                                progressLabel.setText(String.format("Scanning... %d%%", pct));
                            });
                        });
                    }
                };

                task.setOnSucceeded(ev -> {
                    running.set(false);
                    var results = task.getValue();
                    table.getItems().addAll(results);
                    long online = results.size();
                    long elapsed = System.currentTimeMillis() - startTime.get();

                    statsLabel.setText(String.format("Hosts online: %d | Duration: %.2f sec",
                            online, elapsed / 1000.0));
                    progressLabel.setText("Scan completed");
                    progress.setProgress(1.0);

                    DatabaseManager.recordScan("network", cidrVal, online + " hosts online", "");
                    runBtn.setDisable(false);
                    stopBtn.setDisable(true);
                });

                task.setOnFailed(ev -> {
                    running.set(false);
                    progressLabel.setText("Scan failed: " + task.getException().getMessage());
                    runBtn.setDisable(false);
                    stopBtn.setDisable(true);
                });

                new Thread(task, "host-discovery").start();
            });

            stopBtn.setOnAction(e -> {
                stopped.set(true);
                running.set(false);
                progressLabel.setText("Stopped by user");
                runBtn.setDisable(false);
                stopBtn.setDisable(true);
            });

            HBox actionBar = new HBox(12, runBtn, stopBtn);
            VBox resultsBox = new VBox(5, table, statsLabel);

            box.getChildren().addAll(config, actionBar, progress, progressLabel, resultsBox);
            return box;
        }

        private Node dnsPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));

            GridPane config = new GridPane();
            config.setHgap(12);
            config.setVgap(10);
            config.setPadding(new Insets(10, 0, 10, 0));

            TextField host = new TextField("google.com");
            host.setPromptText("Domain or hostname");
            ComboBox<String> type = new ComboBox<>();
            type.getItems().addAll("A", "AAAA", "MX", "TXT", "NS", "CNAME", "SOA", "SRV", "ALL");
            type.setValue("A");

            config.addRow(0, new Label("Host:"), host);
            config.addRow(1, new Label("Record Type:"), type);

            Button runBtn = new Button("Lookup");
            runBtn.getStyleClass().add("button-primary");

            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("DNS records will appear here...");

            Label statsLabel = new Label("Records found: 0 | Time: -");
            statsLabel.setStyle("-fx-text-fill: #6A6A7A; -fx-font-size: 12px;");

            runBtn.setOnAction(e -> {
                output.clear();
                String hostVal = host.getText().trim();
                String typeVal = type.getValue();
                long start = System.currentTimeMillis();

                new Thread(() -> {
                    List<String> results = new ArrayList<>();
                    if ("ALL".equals(typeVal)) {
                        results.add("=== A Records ===");
                        results.addAll(ScanServices.DnsTools.lookupA(hostVal));
                        results.add("\n=== AAAA Records ===");
                        results.addAll(ScanServices.DnsTools.lookupAAAA(hostVal));
                        results.add("\n=== MX Records ===");
                        results.addAll(ScanServices.DnsTools.lookupMX(hostVal));
                        results.add("\n=== TXT Records ===");
                        results.addAll(ScanServices.DnsTools.lookupTXT(hostVal));
                        results.add("\n=== NS Records ===");
                        results.addAll(ScanServices.DnsTools.lookupNS(hostVal));
                        results.add("\n=== CNAME Records ===");
                        results.addAll(ScanServices.DnsTools.lookupCNAME(hostVal));
                        results.add("\n=== SOA Records ===");
                        results.addAll(ScanServices.DnsTools.lookupSOA(hostVal));
                        results.add("\n=== SRV Records ===");
                        results.addAll(ScanServices.DnsTools.lookupSRV(hostVal));
                    } else {
                        switch (typeVal) {
                            case "A": results = ScanServices.DnsTools.lookupA(hostVal); break;
                            case "AAAA": results = ScanServices.DnsTools.lookupAAAA(hostVal); break;
                            case "MX": results = ScanServices.DnsTools.lookupMX(hostVal); break;
                            case "TXT": results = ScanServices.DnsTools.lookupTXT(hostVal); break;
                            case "NS": results = ScanServices.DnsTools.lookupNS(hostVal); break;
                            case "CNAME": results = ScanServices.DnsTools.lookupCNAME(hostVal); break;
                            case "SOA": results = ScanServices.DnsTools.lookupSOA(hostVal); break;
                            case "SRV": results = ScanServices.DnsTools.lookupSRV(hostVal); break;
                            default: results = List.of("Unknown record type");
                        }
                    }

                    long elapsed = System.currentTimeMillis() - start;
                    final List<String> finalResults = results;
                    final long recordCount = results.stream().filter(r -> !r.startsWith("===") && !r.isEmpty()).count();

                    Platform.runLater(() -> {
                        output.setText(String.join("\n", finalResults));
                        statsLabel.setText(String.format("Records found: %d | Time: %d ms", recordCount, elapsed));
                        DatabaseManager.recordScan("network", hostVal, typeVal + " lookup", "");
                    });
                }).start();
            });

            HBox actionBar = new HBox(12, runBtn);
            VBox resultsBox = new VBox(5, output, statsLabel);

            box.getChildren().addAll(config, actionBar, resultsBox);
            return box;
        }
    }

    public static class WebSecurityView extends VBox {
        public WebSecurityView() {
            setSpacing(15);
            setPadding(new Insets(20));
            setStyle("-fx-background-color: #0B0B0E;");
            getChildren().addAll(
                    new Label("Web Security") {{
                        setStyle("-fx-font-size: 24px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;");
                    }},
                    buildTabs()
            );
        }

        private TabPane buildTabs() {
            TabPane tabs = new TabPane();
            tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            tabs.getTabs().addAll(
                    new Tab("Security Headers", headersPane()),
                    new Tab("SSL/TLS Analyzer", sslPane()),
                    new Tab("Tech Fingerprint", techPane())
            );
            return tabs;
        }

        private Node headersPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));

            GridPane config = new GridPane();
            config.setHgap(12);
            config.setVgap(10);
            config.setPadding(new Insets(10, 0, 10, 0));

            TextField url = new TextField("https://example.com");
            url.setPromptText("https://example.com");

            config.addRow(0, new Label("URL:"), url);

            Button runBtn = new Button("Analyze Headers");
            runBtn.getStyleClass().add("button-primary");

            TableView<ScanServices.SecurityHeadersAnalyzer.HeaderCheck> table = new TableView<>();
            TableColumn<ScanServices.SecurityHeadersAnalyzer.HeaderCheck, String> colH = new TableColumn<>("Header");
            colH.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().header()));
            colH.setPrefWidth(220);
            TableColumn<ScanServices.SecurityHeadersAnalyzer.HeaderCheck, Boolean> colP = new TableColumn<>("Present");
            colP.setCellValueFactory(c -> new javafx.beans.property.SimpleObjectProperty<>(c.getValue().present()));
            colP.setPrefWidth(80);
            TableColumn<ScanServices.SecurityHeadersAnalyzer.HeaderCheck, String> colV = new TableColumn<>("Value");
            colV.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().value()));
            colV.setPrefWidth(280);
            TableColumn<ScanServices.SecurityHeadersAnalyzer.HeaderCheck, String> colRec = new TableColumn<>("Recommendation");
            colRec.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().recommendation()));
            colRec.setPrefWidth(200);
            table.getColumns().addAll(colH, colP, colV, colRec);
            table.setPrefHeight(280);
            table.setPlaceholder(new Label("No results yet. Run an analysis."));

            Label scoreLabel = new Label("Score: -");
            scoreLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: 700; -fx-text-fill: #F5C96A;");

            Label statsLabel = new Label("Headers checked: 0 | Missing: 0");
            statsLabel.setStyle("-fx-text-fill: #6A6A7A; -fx-font-size: 12px;");

            runBtn.setOnAction(e -> {
                table.getItems().clear();
                scoreLabel.setText("Analyzing...");

                new Thread(() -> {
                    try {
                        var report = ScanServices.SecurityHeadersAnalyzer.analyze(url.getText().trim());
                        Platform.runLater(() -> {
                            table.getItems().addAll(report.checks());
                            long missing = report.checks().stream().filter(c -> !c.present()).count();
                            scoreLabel.setText("Score: " + report.score() + "/100");
                            statsLabel.setText(String.format("Headers checked: %d | Missing: %d",
                                    report.checks().size(), missing));
                            DatabaseManager.recordScan("websec", url.getText().trim(), "headers score " + report.score(), "");
                        });
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            scoreLabel.setText("Error: " + ex.getMessage());
                            statsLabel.setText("Analysis failed");
                        });
                    }
                }).start();
            });

            HBox actionBar = new HBox(12, runBtn);
            VBox resultsBox = new VBox(5, scoreLabel, table, statsLabel);

            box.getChildren().addAll(config, actionBar, resultsBox);
            return box;
        }

        private Node sslPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));

            GridPane config = new GridPane();
            config.setHgap(12);
            config.setVgap(10);
            config.setPadding(new Insets(10, 0, 10, 0));

            TextField host = new TextField("example.com");
            host.setPromptText("Host or domain");

            config.addRow(0, new Label("Host:"), host);

            Button runBtn = new Button("Analyze SSL/TLS");
            runBtn.getStyleClass().add("button-primary");

            TextArea output = new TextArea();
            output.setEditable(false);
            output.setPrefHeight(350);
            output.getStyleClass().add("console-output");
            output.setPromptText("SSL/TLS analysis results will appear here...");

            Label statsLabel = new Label("Analysis time: -");
            statsLabel.setStyle("-fx-text-fill: #6A6A7A; -fx-font-size: 12px;");

            runBtn.setOnAction(e -> {
                output.clear();
                String hostVal = host.getText().trim();
                long start = System.currentTimeMillis();

                new Thread(() -> {
                    var r = ScanServices.HttpsInspector.inspect(hostVal);
                    long elapsed = System.currentTimeMillis() - start;

                    Platform.runLater(() -> {
                        StringBuilder sb = new StringBuilder();
                        sb.append("SSL/TLS Analysis Report\n");
                        sb.append("========================\n\n");
                        sb.append("Target: ").append(hostVal).append("\n");
                        sb.append("HTTPS Available: ").append(r.httpsAvailable() ? "Yes" : "No").append("\n");
                        sb.append("HTTPS Redirect: ").append(r.redirectsFromHttp() ? "Yes" : "No").append("\n");
                        sb.append("Certificate Valid: ").append(r.certValid() ? "Yes" : "No").append("\n");
                        sb.append("Expiration: ").append(r.expiration()).append("\n");
                        sb.append("Issuer: ").append(r.issuer()).append("\n");
                        sb.append("Subject: ").append(r.subject()).append("\n");
                        sb.append("Protocol: ").append(r.protocol()).append("\n");
                        sb.append("Cipher Suite: ").append(r.cipherSuite()).append("\n");
                        sb.append("SAN: ").append(String.join(", ", r.san())).append("\n");

                        int rating = 0;
                        if (r.httpsAvailable()) rating += 20;
                        if (r.redirectsFromHttp()) rating += 20;
                        if (r.certValid()) rating += 30;
                        if (r.protocol() != null && r.protocol().contains("TLSv1.3")) rating += 30;
                        else if (r.protocol() != null && r.protocol().contains("TLSv1.2")) rating += 15;
                        sb.append("\nSecurity Rating: ").append(rating).append("/100");
                        if (rating >= 80) sb.append(" (Excellent)");
                        else if (rating >= 60) sb.append(" (Good)");
                        else if (rating >= 40) sb.append(" (Fair)");
                        else sb.append(" (Poor)");

                        output.setText(sb.toString());
                        statsLabel.setText(String.format("Analysis time: %d ms", elapsed));
                        DatabaseManager.recordScan("websec", hostVal, "ssl/tls analysis", "");
                    });
                }).start();
            });

            HBox actionBar = new HBox(12, runBtn);
            VBox resultsBox = new VBox(5, output, statsLabel);

            box.getChildren().addAll(config, actionBar, resultsBox);
            return box;
        }

        private Node techPane() {
            VBox box = new VBox(10);
            box.setPadding(new Insets(15));

            GridPane config = new GridPane();
            config.setHgap(12);
            config.setVgap(10);
            config.setPadding(new Insets(10, 0, 10, 0));

            TextField url = new TextField("https://example.com");
            url.setPromptText("https://example.com");

            config.addRow(0, new Label("URL:"), url);

            Button runBtn = new Button("Fingerprint");
            runBtn.getStyleClass().add("button-primary");

            ListView<String> list = new ListView<>();
            list.setPrefHeight(350);
            list.setPlaceholder(new Label("No results yet. Run fingerprinting."));

            Label statsLabel = new Label("Technologies found: 0");
            statsLabel.setStyle("-fx-text-fill: #6A6A7A; -fx-font-size: 12px;");

            runBtn.setOnAction(e -> {
                list.getItems().clear();
                String urlVal = url.getText().trim();

                new Thread(() -> {
                    var techs = ScanServices.TechFingerprinter.fingerprint(urlVal);
                    Platform.runLater(() -> {
                        list.getItems().addAll(techs);
                        statsLabel.setText(String.format("Technologies found: %d", techs.size()));
                        DatabaseManager.recordScan("websec", urlVal, "tech fingerprint", "");
                    });
                }).start();
            });

            HBox actionBar = new HBox(12, runBtn);
            VBox resultsBox = new VBox(5, list, statsLabel);

            box.getChildren().addAll(config, actionBar, resultsBox);
            return box;
        }
    }
}
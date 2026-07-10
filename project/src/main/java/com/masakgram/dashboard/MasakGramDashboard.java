package com.masakgram.dashboard;

import com.formdev.flatlaf.FlatLightLaf;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.ListSelectionEvent;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MasakGramDashboard extends JFrame {

    private static final long serialVersionUID = 1L;

    // Light Theme UI Palette
    private static final Color BG_DARK = new Color(245, 247, 250);
    private static final Color BG_SIDEBAR = new Color(225, 230, 239);
    private static final Color CARD_BG = new Color(255, 255, 255);
    private static final Color BLUE_ACCENT = new Color(0, 123, 255);
    private static final Color TEXT_LIGHT = new Color(33, 37, 41);
    private static final Color BORDER_COLOR = new Color(210, 216, 225);
    
    // Colors for ingredient status
    private static final Color COLOR_MATCHED = new Color(40, 167, 69);
    private static final Color COLOR_HALLUCINATED = new Color(220, 50, 50);
    private static final Color COLOR_MISSING = new Color(255, 140, 0);
    private static final Color COLOR_UNKNOWN = new Color(150, 150, 150);

    private JPanel mainContentCardPanel;
    private CardLayout cardLayout;

    // Core Operational UI Components
    private DefaultTableModel matrixTableModel;
    private JTable matrixTable;
    private JTextArea consoleArea;
    private JComboBox<String> matrixPromptFilter;

    // Dynamic Live Metric Labels
    private JLabel lblStatTranscripts;
    private JLabel lblStatExperiments;
    private JLabel lblStatSuccessFailure;

    // Detail Panel UI Components
    private JLabel lblDetailId;
    private JLabel lblDetailName;
    private JLabel lblDetailStatus;
    private JLabel lblDetailHallucinate;
    private JTextArea txtLlmNutrition;
    private JTextArea txtCombinedIngredients;
    private JTextArea txtHallucinationReason;

    private static final String[] MODELS = {"Llama 3.2 (3B)", "Phi-4-mini (3.8B)", "Qwen 2.5 (3B)", "Gemma-SEA-LION v4 (4B)", "MedGemma (4B)"};
    private static final String[] TECHNIQUES = {"zero-shot", "few-shot", "chain-of-thought", "structured-output"};
    
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 12345;
    private ObjectMapper mapper = new ObjectMapper();

    public MasakGramDashboard() {
        setTitle("MasakGramPrompt - Experiment Execution Engine");
        setSize(1300, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Build UI
        JPanel sidebar = buildSidebar();
        mainContentCardPanel = new JPanel(cardLayout);

        mainContentCardPanel.add(buildRunExperimentPanel(), "RUN_PANEL");
        mainContentCardPanel.add(buildMatrixStatusPanel(), "MATRIX_PANEL");

        add(sidebar, BorderLayout.WEST);
        add(mainContentCardPanel, BorderLayout.CENTER);

        refreshAllData();
        startAutoRefresh();
    }

    // ============================================================
    // UI BUILDING METHODS
    // ============================================================

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setBackground(BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(220, getHeight()));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR),
            BorderFactory.createEmptyBorder(25, 10, 25, 10)
        ));

        JLabel brandLabel = new JLabel("MasakGram Engine");
        brandLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        brandLabel.setForeground(TEXT_LIGHT);
        brandLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        sidebar.add(brandLabel);
        sidebar.add(Box.createVerticalStrut(35));

        cardLayout = new CardLayout();
        mainContentCardPanel = new JPanel(cardLayout);

        createSidebarMenuButton(sidebar, "[=] Status Matrix", "MATRIX_PANEL");
        createSidebarMenuButton(sidebar, "[>] Run Experiment", "RUN_PANEL");

        sidebar.add(Box.createVerticalStrut(20));
        JButton btnExportCsv = new JButton("Export to CSV");
        btnExportCsv.setMaximumSize(new Dimension(200, 42));
        btnExportCsv.setAlignmentX(Component.CENTER_ALIGNMENT);
        btnExportCsv.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnExportCsv.setBackground(new Color(40, 167, 69));
        btnExportCsv.setForeground(Color.WHITE);
        btnExportCsv.setFocusable(false);
        btnExportCsv.addActionListener(e -> exportToCSV());
        sidebar.add(btnExportCsv);

        return sidebar;
    }

    private void createSidebarMenuButton(JPanel sidebar, String text, String cardName) {
        JButton btn = new JButton(text);
        btn.setMaximumSize(new Dimension(200, 42));
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setFocusable(false);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.addActionListener(e -> {
            cardLayout.show(mainContentCardPanel, cardName);
            refreshAllData();
        });
        sidebar.add(btn);
        sidebar.add(Box.createVerticalStrut(10));
    }

    private JPanel buildRunExperimentPanel() {
        JPanel panel = new JPanel(new BorderLayout(20, 20));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(25, 25, 25, 25));

        JLabel titleLbl = new JLabel("Configure & Dispatch LLM Batch Processing");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLbl.setForeground(TEXT_LIGHT);
        panel.add(titleLbl, BorderLayout.NORTH);

        JPanel centerFormPanel = new JPanel(new BorderLayout(15, 15));
        centerFormPanel.setBackground(BG_DARK);

        JPanel dropdownsGrid = new JPanel(new GridLayout(2, 2, 15, 15));
        dropdownsGrid.setBackground(BG_DARK);

        JLabel modelLbl = new JLabel("Select Target LLM Model:");
        modelLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        modelLbl.setForeground(TEXT_LIGHT);
        JComboBox<String> modelCombo = new JComboBox<>(MODELS);

        JLabel techLbl = new JLabel("Select Target Prompt Technique:");
        techLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        techLbl.setForeground(TEXT_LIGHT);
        JComboBox<String> techCombo = new JComboBox<>(TECHNIQUES);

        dropdownsGrid.add(modelLbl);
        dropdownsGrid.add(modelCombo);
        dropdownsGrid.add(techLbl);
        dropdownsGrid.add(techCombo);
        centerFormPanel.add(dropdownsGrid, BorderLayout.NORTH);

        consoleArea = new JTextArea();
        consoleArea.setBackground(CARD_BG);
        consoleArea.setForeground(new Color(0, 102, 204));
        consoleArea.setFont(new Font("Consolas", Font.BOLD, 13));
        consoleArea.setEditable(false);
        consoleArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_COLOR), "Live Server Output Terminal"));
        consoleScroll.setPreferredSize(new Dimension(0, 300));
        centerFormPanel.add(consoleScroll, BorderLayout.CENTER);

        panel.add(centerFormPanel, BorderLayout.CENTER);

        JButton runBtn = new JButton("EXECUTE -> Launch Live Distributed Pipeline over TCP");
        runBtn.setBackground(BLUE_ACCENT);
        runBtn.setForeground(Color.WHITE);
        runBtn.setFont(new Font("Segoe UI", Font.BOLD, 15));
        runBtn.setPreferredSize(new Dimension(0, 48));

        runBtn.addActionListener(e -> {
            String chosenModel = modelCombo.getSelectedItem().toString();
            String chosenTech = techCombo.getSelectedItem().toString();

            new Thread(() -> {
                runBtn.setEnabled(false);
                consoleArea.setText("[Client Process] Connecting to MasakGramServer (localhost:12345)...\n");
                String response = sendRequest("BATCH_EXECUTE", chosenModel + "|" + chosenTech);
                if (response != null) {
                    consoleArea.append(response + "\n");
                }
                runBtn.setEnabled(true);
                refreshAllData();
            }).start();
        });

        panel.add(runBtn, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildMatrixStatusPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 15));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel topContainer = new JPanel(new BorderLayout(0, 12));
        topContainer.setBackground(BG_DARK);

        JPanel headerBar = new JPanel(new BorderLayout());
        headerBar.setBackground(BG_DARK);

        JPanel titleTextPanel = new JPanel(new BorderLayout(0, 4));
        titleTextPanel.setBackground(BG_DARK);

        JLabel titleLabel = new JLabel("Transcript Execution Status Matrix");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(TEXT_LIGHT);
        JLabel subLabel = new JLabel("Cross-tabulated view mapping models against verifier assignments.");
        subLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subLabel.setForeground(Color.DARK_GRAY);
        titleTextPanel.add(titleLabel, BorderLayout.NORTH);
        titleTextPanel.add(subLabel, BorderLayout.SOUTH);
        headerBar.add(titleTextPanel, BorderLayout.WEST);

        JPanel selectorWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        selectorWrapper.setBackground(BG_DARK);
        JLabel filterLabel = new JLabel("Active Prompt Matrix:");
        filterLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        matrixPromptFilter = new JComboBox<>(TECHNIQUES);
        matrixPromptFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        matrixPromptFilter.setPreferredSize(new Dimension(220, 36));
        matrixPromptFilter.addActionListener(e -> {
            refreshMatrixStatusTable();
            refreshMetrics();
        });

        selectorWrapper.add(filterLabel);
        selectorWrapper.add(matrixPromptFilter);
        headerBar.add(selectorWrapper, BorderLayout.EAST);
        topContainer.add(headerBar, BorderLayout.NORTH);

        JPanel metricsRibbon = new JPanel(new GridLayout(1, 3, 15, 0));
        metricsRibbon.setBackground(BG_DARK);
        lblStatTranscripts = createMetricBadge("Total Transcripts", "0", Color.DARK_GRAY);
        lblStatExperiments = createMetricBadge("Experiments Run", "0", BLUE_ACCENT);
        lblStatSuccessFailure = createMetricBadge("Technique Success / Failure Rate (%)", "0.0% / 0.0%", new Color(100, 40, 167));

        metricsRibbon.add(lblStatTranscripts.getParent());
        metricsRibbon.add(lblStatExperiments.getParent());
        metricsRibbon.add(lblStatSuccessFailure.getParent());
        topContainer.add(metricsRibbon, BorderLayout.SOUTH);

        panel.add(topContainer, BorderLayout.NORTH);

        String[] cols = {"ID", "Transcript File", "Llama 3.2", "Phi-4-mini", "Qwen 2.5", "Gemma-SEA", "MedGemma"};
        matrixTableModel = new DefaultTableModel(null, cols) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int r, int c) { return false; }
        };

        matrixTable = new JTable(matrixTableModel);
        matrixTable.setRowHeight(34);
        matrixTable.setBackground(CARD_BG);
        matrixTable.setForeground(TEXT_LIGHT);
        matrixTable.setGridColor(BORDER_COLOR);
        matrixTable.setCellSelectionEnabled(true);
        matrixTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        matrixTable.getSelectionModel().addListSelectionListener(this::onTableSelectionChanged);
        matrixTable.getColumnModel().getSelectionModel().addListSelectionListener(this::onTableSelectionChanged);

        StatusCellRenderer matrixRenderer = new StatusCellRenderer();
        for (int i = 2; i < cols.length; i++) {
            matrixTable.getColumnModel().getColumn(i).setCellRenderer(matrixRenderer);
        }

        JScrollPane tableScroll = new JScrollPane(matrixTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));

        // ============================================================
        // COMBINED DETAIL PANEL - Nutrition + Combined Ingredients + Hallucination Reason
        // ============================================================
        JPanel detailWorkspacePanel = buildCombinedDetailPanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailWorkspacePanel);
        splitPane.setDividerLocation(320);
        splitPane.setResizeWeight(0.5);
        splitPane.setBorder(null);

        panel.add(splitPane, BorderLayout.CENTER);

        JPanel legendPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 25, 5));
        legendPanel.setBackground(BG_DARK);
        legendPanel.add(createLegendLabel("- Unexecuted (-)", Color.GRAY));
        legendPanel.add(createLegendLabel("* Running", BLUE_ACCENT));
        legendPanel.add(createLegendLabel("* Completed", new Color(40, 167, 69)));
        legendPanel.add(createLegendLabel("* Failed", Color.RED));
        panel.add(legendPanel, BorderLayout.SOUTH);

        return panel;
    }


    // ============================================================
    // COMBINED DETAIL PANEL
    // ============================================================
    private JPanel buildCombinedDetailPanel() {
        JPanel detailPanel = new JPanel(new BorderLayout(10, 10));
        detailPanel.setBackground(CARD_BG);
        detailPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(12, 15, 12, 15)
        ));

        // Header with metadata
        JPanel headerMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 3));
        headerMeta.setBackground(CARD_BG);

        lblDetailId = new JLabel("Transcript ID: --");
        lblDetailId.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDetailName = new JLabel("File: Select an execution cell...");
        lblDetailName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDetailStatus = new JLabel("Status: --");
        lblDetailStatus.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDetailHallucinate = new JLabel("🔍 Hallucination: --");
        lblDetailHallucinate.setFont(new Font("Segoe UI", Font.BOLD, 13));
        lblDetailHallucinate.setForeground(Color.GRAY);

        headerMeta.add(lblDetailId);
        headerMeta.add(lblDetailName);
        headerMeta.add(lblDetailStatus);
        headerMeta.add(lblDetailHallucinate);
        detailPanel.add(headerMeta, BorderLayout.NORTH);

        // ============================================================
        // MAIN BODY: Nutrition (Left) + Combined Ingredients (Right)
        // ============================================================
        // Wrapped in a horizontal JSplitPane (instead of a fixed GridLayout) so the
        // user gets a draggable divider handle to resize Nutrition vs Ingredients width.

        // LEFT: Nutrition Summary
        JPanel nutritionPanel = new JPanel(new BorderLayout());
        nutritionPanel.setBackground(CARD_BG);
        JLabel nutritionTitle = new JLabel("📊 Nutrition Summary");
        nutritionTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        nutritionTitle.setForeground(new Color(0, 102, 204));
        
        txtLlmNutrition = new JTextArea();
        txtLlmNutrition.setEditable(false);
        txtLlmNutrition.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtLlmNutrition.setBackground(new Color(248, 248, 255));
        txtLlmNutrition.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        txtLlmNutrition.setText("Select a model cell to view...");
        
        nutritionPanel.add(nutritionTitle, BorderLayout.NORTH);
        nutritionPanel.add(new JScrollPane(txtLlmNutrition), BorderLayout.CENTER);
        nutritionPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0, 102, 204, 80), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        // RIGHT: Combined Ingredients with Hallucination Analysis
        JPanel combinedPanel = new JPanel(new BorderLayout());
        combinedPanel.setBackground(CARD_BG);
        
        JPanel combinedHeader = new JPanel(new BorderLayout());
        combinedHeader.setBackground(CARD_BG);
        JLabel combinedTitle = new JLabel("🔬 Ingredients Analysis");
        combinedTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        combinedTitle.setForeground(new Color(150, 50, 50));
        combinedHeader.add(combinedTitle, BorderLayout.WEST);
        
        // Legend for the combined view
        JPanel legendMini = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        legendMini.setBackground(CARD_BG);
        legendMini.add(createMiniLegend("✅", "Matched", COLOR_MATCHED));
        legendMini.add(createMiniLegend("⚠️", "Hallucinated", COLOR_HALLUCINATED));
        legendMini.add(createMiniLegend("❌", "Missing", COLOR_MISSING));
        combinedHeader.add(legendMini, BorderLayout.EAST);
        
        combinedPanel.add(combinedHeader, BorderLayout.NORTH);

        // Combined ingredients text area
        txtCombinedIngredients = new JTextArea();
        txtCombinedIngredients.setEditable(false);
        txtCombinedIngredients.setFont(new Font("Consolas", Font.PLAIN, 12));
        txtCombinedIngredients.setBackground(new Color(255, 252, 248));
        txtCombinedIngredients.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        txtCombinedIngredients.setText("Select a model cell to view analysis...");
        
        combinedPanel.add(new JScrollPane(txtCombinedIngredients), BorderLayout.CENTER);
        combinedPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(150, 50, 50, 80), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));

        JSplitPane bodySplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, nutritionPanel, combinedPanel);
        bodySplitPane.setResizeWeight(0.35);   // Ingredients panel starts a bit wider since its content is denser
        bodySplitPane.setContinuousLayout(true);
        bodySplitPane.setBorder(null);
        bodySplitPane.setBackground(CARD_BG);
        detailPanel.add(bodySplitPane, BorderLayout.CENTER);

        // ============================================================
        // BOTTOM: Hallucination Reason Explanation
        // ============================================================
        JPanel reasonPanel = new JPanel(new BorderLayout());
        reasonPanel.setBackground(CARD_BG);
        reasonPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
            BorderFactory.createEmptyBorder(8, 5, 0, 5)
        ));
        
        JLabel reasonTitle = new JLabel("💡 Hallucination Analysis");
        reasonTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        reasonTitle.setForeground(COLOR_HALLUCINATED);
        reasonPanel.add(reasonTitle, BorderLayout.NORTH);
        
        txtHallucinationReason = new JTextArea();
        txtHallucinationReason.setEditable(false);
        txtHallucinationReason.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        txtHallucinationReason.setBackground(CARD_BG);
        txtHallucinationReason.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        txtHallucinationReason.setText("Analysis will appear here when an experiment is selected...");
        txtHallucinationReason.setLineWrap(true);
        txtHallucinationReason.setWrapStyleWord(true);
        
        JScrollPane reasonScroll = new JScrollPane(txtHallucinationReason);
        reasonScroll.setBorder(null);
        reasonScroll.setPreferredSize(new Dimension(0, 160)); // fixed height — content scrolls inside, never grows the panel
        reasonScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        reasonScroll.getVerticalScrollBar().setUnitIncrement(16);

        reasonPanel.add(reasonScroll, BorderLayout.CENTER);
        detailPanel.add(reasonPanel, BorderLayout.SOUTH);

        return detailPanel;
    }

    private JPanel createMiniLegend(String icon, String label, Color color) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        panel.setBackground(CARD_BG);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JLabel textLabel = new JLabel(label);
        textLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        textLabel.setForeground(color);
        panel.add(iconLabel);
        panel.add(textLabel);
        return panel;
    }

    private JTextArea createDetailsTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        area.setBackground(BG_DARK);
        area.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        return area;
    }

    private JPanel createTitledPanelWrapper(JComponent component, String title) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(CARD_BG);
        wrapper.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(BORDER_COLOR), title));
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    private JLabel createMetricBadge(String title, String defaultValue, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(CARD_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1), BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        JLabel titleLbl = new JLabel(title.toUpperCase());
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(Color.GRAY);
        JLabel valLbl = new JLabel(defaultValue);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valLbl.setForeground(valueColor);
        card.add(titleLbl, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        return valLbl;
    }

    private JLabel createLegendLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(color);
        return label;
    }

    // ============================================================
    // CLIENT-SERVER COMMUNICATION
    // ============================================================

    private String sendRequest(String command, String data) {
        try (Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println(command + "|" + data);
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line).append("\n");
                if (line.startsWith("STATUS|COMPLETED") || line.startsWith("ERROR") || 
                    line.startsWith("SUCCESS|") || line.startsWith("DATA|")) {
                    break;
                }
            }
            return response.toString().trim();
        } catch (Exception e) {
            return "ERROR|" + e.getMessage();
        }
    }

    // ============================================================
    // DATA REFRESH METHODS
    // ============================================================

    private void refreshAllData() {
        refreshMatrixStatusTable();
        refreshMetrics();
        clearDetailsPanel();
    }

    private void startAutoRefresh() {
        Timer timer = new Timer(3000, e -> {
            refreshMatrixStatusTable();
            refreshMetrics();
        });
        timer.start();
    }

    private void refreshMatrixStatusTable() {
        if (matrixTableModel == null) return;

        String selectedTechnique = matrixPromptFilter.getSelectedItem().toString();
        String response = sendRequest("GET_MATRIX", selectedTechnique);

        matrixTableModel.setRowCount(0);

        if (response.startsWith("SUCCESS|")) {
            try {
                String jsonData = response.substring(8);
                JsonNode root = mapper.readTree(jsonData);
                JsonNode dataNode = root.get("data");

                if (dataNode != null && dataNode.isArray()) {
                    for (JsonNode row : dataNode) {
                        Object[] rowData = new Object[7];
                        rowData[0] = row.get("transcript_id").asInt();
                        rowData[1] = row.has("transcript_file") ? row.get("transcript_file").asText() : "No Name";
                        rowData[2] = row.has("Llama 3.2") ? row.get("Llama 3.2").asText() : "-";
                        rowData[3] = row.has("Phi-4-mini") ? row.get("Phi-4-mini").asText() : "-";
                        rowData[4] = row.has("Qwen 2.5") ? row.get("Qwen 2.5").asText() : "-";
                        rowData[5] = row.has("Gemma-SEA") ? row.get("Gemma-SEA").asText() : "-";
                        rowData[6] = row.has("MedGemma") ? row.get("MedGemma").asText() : "-";
                        matrixTableModel.addRow(rowData);
                    }
                }
            } catch (Exception e) {
                System.err.println("Error parsing matrix data: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Error fetching matrix: " + response);
        }
    }

    private void refreshMetrics() {
        if (lblStatTranscripts == null || matrixPromptFilter == null) return;

        String selectedTechnique = matrixPromptFilter.getSelectedItem().toString();
        String response = sendRequest("GET_METRICS", selectedTechnique);

        if (response.startsWith("SUCCESS|")) {
            try {
                String jsonData = response.substring(8);
                JsonNode metrics = mapper.readTree(jsonData);

                lblStatTranscripts.setText(metrics.has("total_transcripts") ? String.valueOf(metrics.get("total_transcripts").asInt()) : "0");
                lblStatExperiments.setText(metrics.has("total_experiments") ? String.valueOf(metrics.get("total_experiments").asInt()) : "0");

                String successRate = metrics.has("success_rate") ? metrics.get("success_rate").asText() : "0.0%";
                String failureRate = metrics.has("failure_rate") ? metrics.get("failure_rate").asText() : "0.0%";
                lblStatSuccessFailure.setText(successRate + " / " + failureRate);
            } catch (Exception e) {
                System.err.println("Error parsing metrics: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Error fetching metrics: " + response);
        }
    }

 // ============================================================
 // FETCH DETAILS - WITH PROPER HALLUCINATION DISPLAY
 // ============================================================
 private void fetchAndDisplaySchemaDetails(int transcriptId, String modelHeaderName, String techniqueName) {
     clearDetailsPanel();

     String data = transcriptId + "|" + modelHeaderName + "|" + techniqueName;
     String response = sendRequest("GET_DETAILS", data);

     System.out.println("=== FETCH DETAILS RESPONSE ===");
     if (response == null) {
         System.err.println("Response is null!");
         return;
     }
     System.out.println("Response: " + response.substring(0, Math.min(200, response.length())));

     if (response.startsWith("SUCCESS|")) {
         try {
             String jsonData = response.substring(8);
             JsonNode details = mapper.readTree(jsonData);

             // Update header
             lblDetailId.setText("Transcript ID: " + details.get("transcript_id").asInt());
             lblDetailName.setText("File: " + (details.has("file_name") ? details.get("file_name").asText() : "Unknown"));
             lblDetailStatus.setText("Status: " + (details.has("status") ? details.get("status").asText().toUpperCase() : "UNKNOWN"));

             // ============================================================
             // NUTRITION SUMMARY
             // ============================================================
             StringBuilder nutritionText = new StringBuilder();
             JsonNode nutrition = details.get("nutrition");
             if (nutrition != null && !nutrition.isNull()) {
                 nutritionText.append("Recipe: ").append(nutrition.has("recipe_name") && !nutrition.get("recipe_name").isNull() ? nutrition.get("recipe_name").asText() : "N/A").append("\n");
                 nutritionText.append("Servings: ").append(nutrition.has("servings") && !nutrition.get("servings").isNull() ? nutrition.get("servings").asInt() : "N/A").append("\n\n");
                 nutritionText.append("Calories: ").append(nutrition.has("calories") && !nutrition.get("calories").isNull() ? String.format("%.1f", nutrition.get("calories").asDouble()) : "N/A").append(" kcal\n");
                 nutritionText.append("Protein: ").append(nutrition.has("protein") && !nutrition.get("protein").isNull() ? String.format("%.1f", nutrition.get("protein").asDouble()) : "N/A").append(" g\n");
                 nutritionText.append("Carbs: ").append(nutrition.has("carbs") && !nutrition.get("carbs").isNull() ? String.format("%.1f", nutrition.get("carbs").asDouble()) : "N/A").append(" g\n");
                 nutritionText.append("Fat: ").append(nutrition.has("fat") && !nutrition.get("fat").isNull() ? String.format("%.1f", nutrition.get("fat").asDouble()) : "N/A").append(" g");
             } else {
                 nutritionText.append("No nutrition data available");
             }
             txtLlmNutrition.setText(nutritionText.toString());

             // ============================================================
             // GET HALLUCINATION ANALYSIS
             // ============================================================
             JsonNode hallucinationAnalysis = details.get("hallucination_analysis");
             
             // Debug output
             System.out.println("hallucinationAnalysis exists: " + (hallucinationAnalysis != null));
             if (hallucinationAnalysis != null) {
                 System.out.println("hallucinationAnalysis: " + hallucinationAnalysis.toString());
             }

             if (hallucinationAnalysis != null && !hallucinationAnalysis.isNull()) {
                 // Get the arrays
                 JsonNode hallucinated = hallucinationAnalysis.get("hallucinated");
                 JsonNode missing = hallucinationAnalysis.get("missing");
                 JsonNode matched = hallucinationAnalysis.get("matched");
                 
                 System.out.println("Hallucinated count: " + (hallucinated != null ? hallucinated.size() : 0));
                 System.out.println("Missing count: " + (missing != null ? missing.size() : 0));
                 System.out.println("Matched count: " + (matched != null ? matched.size() : 0));

                 // ============================================================
                 // BUILD COMBINED INGREDIENTS DISPLAY
                 // ============================================================
                 StringBuilder combinedText = new StringBuilder();
                 
                 if ((hallucinated != null && hallucinated.size() > 0) || 
                     (missing != null && missing.size() > 0) || 
                     (matched != null && matched.size() > 0)) {
                     
                     combinedText.append("┌─────────────────────────────────────────────────────────────────┐\n");
                     combinedText.append("│  LLM PREDICTED                    │  GROUND TRUTH              │\n");
                     combinedText.append("├─────────────────────────────────────────────────────────────────┤\n");

                     // Display matched pairs
                     if (matched != null && matched.isArray()) {
                         for (JsonNode m : matched) {
                             String llmName = m.has("llm_name") ? m.get("llm_name").asText() : "Unknown";
                             String gtName = m.has("gt_name") ? m.get("gt_name").asText() : "Unknown";
                             combinedText.append("│ ✅ ").append(padRight(llmName, 30));
                             combinedText.append(" │ ").append(padRight(gtName, 25)).append("│\n");
                         }
                     }

                     // Display hallucinated ingredients
                     if (hallucinated != null && hallucinated.isArray()) {
                         for (JsonNode h : hallucinated) {
                             String name = h.has("name") ? h.get("name").asText() : "Unknown";
                             combinedText.append("│ ⚠️ ").append(padRight(name + " [HALLUCINATED]", 30));
                             combinedText.append(" │ ").append(padRight("(NOT IN GT)", 25)).append("│\n");
                         }
                     }

                     // Display missing ingredients
                     if (missing != null && missing.isArray()) {
                         for (JsonNode m : missing) {
                             String name = m.has("name") ? m.get("name").asText() : "Unknown";
                             combinedText.append("│ ❌ ").append(padRight("(MISSING)", 30));
                             combinedText.append(" │ ").append(padRight(name, 25)).append("│\n");
                         }
                     }

                     combinedText.append("└─────────────────────────────────────────────────────────────────┘\n");

                     // Add statistics
                     int totalLLM = hallucinationAnalysis.has("total_llm_ingredients") ? 
                         hallucinationAnalysis.get("total_llm_ingredients").asInt() : 0;
                     int totalGT = hallucinationAnalysis.has("total_gt_ingredients") ? 
                         hallucinationAnalysis.get("total_gt_ingredients").asInt() : 0;
                     int hallucinatedCount = hallucinationAnalysis.has("hallucinated_count") ? 
                         hallucinationAnalysis.get("hallucinated_count").asInt() : 0;
                     int missingCount = hallucinationAnalysis.has("missing_count") ? 
                         hallucinationAnalysis.get("missing_count").asInt() : 0;
                     int matchedCount = hallucinationAnalysis.has("matched_count") ? 
                         hallucinationAnalysis.get("matched_count").asInt() : 0;

                     combinedText.append("\n📊 Summary: ");
                     combinedText.append("LLM: ").append(totalLLM).append(" | ");
                     combinedText.append("GT: ").append(totalGT).append(" | ");
                     combinedText.append("✅ Matched: ").append(matchedCount).append(" | ");
                     combinedText.append("⚠️ Hallucinated: ").append(hallucinatedCount).append(" | ");
                     combinedText.append("❌ Missing: ").append(missingCount);
                 } else {
                     combinedText.append("✅ All ingredients matched correctly!");
                 }
                 
                 txtCombinedIngredients.setText(combinedText.toString());

                 // ============================================================
                 // HALLUCINATION REASON
                 // ============================================================
                 StringBuilder reasonText = new StringBuilder();
                 String status = hallucinationAnalysis.has("status") ? 
                     hallucinationAnalysis.get("status").asText() : "UNKNOWN";
                 String summary = hallucinationAnalysis.has("summary") ? 
                     hallucinationAnalysis.get("summary").asText() : "";

                 reasonText.append("Status: ").append(status).append("\n");
                 reasonText.append("Summary: ").append(summary).append("\n\n");

                 // Build detailed report
                 if (hallucinated != null && hallucinated.size() > 0) {
                     reasonText.append("⚠️ HALLUCINATED INGREDIENTS (Extra):\n");
                     for (JsonNode h : hallucinated) {
                         String name = h.has("name") ? h.get("name").asText() : "Unknown";
                         String reason = h.has("reason") ? h.get("reason").asText() : "Not in GT";
                         reasonText.append("  • ").append(name).append(" → ").append(reason).append("\n");
                     }
                     reasonText.append("\n");
                 }

                 if (missing != null && missing.size() > 0) {
                     reasonText.append("❌ MISSING INGREDIENTS (Not Predicted):\n");
                     for (JsonNode m : missing) {
                         String name = m.has("name") ? m.get("name").asText() : "Unknown";
                         String reason = m.has("reason") ? m.get("reason").asText() : "Not predicted";
                         reasonText.append("  • ").append(name).append(" → ").append(reason).append("\n");
                     }
                     reasonText.append("\n");
                 }

                 if (matched != null && matched.size() > 0) {
                     reasonText.append("✅ CORRECTLY MATCHED:\n");
                     for (JsonNode m : matched) {
                         String llmName = m.has("llm_name") ? m.get("llm_name").asText() : "Unknown";
                         String gtName = m.has("gt_name") ? m.get("gt_name").asText() : "Unknown";
                         reasonText.append("  • ").append(llmName).append(" ↔ ").append(gtName).append("\n");
                     }
                 }

                 txtHallucinationReason.setText(reasonText.toString());

                 // Update hallucination status label
                 String displayStatus = status.replace("_", " ");
                 lblDetailHallucinate.setText("🔍 Hallucination: " + displayStatus);
                 
                 if (status.equals("NO_HALLUCINATION")) {
                     lblDetailHallucinate.setForeground(new Color(40, 167, 69));
                 } else if (status.startsWith("HALLUCINATION")) {
                     lblDetailHallucinate.setForeground(Color.RED);
                 } else {
                     lblDetailHallucinate.setForeground(Color.GRAY);
                 }

             } else {
                 txtCombinedIngredients.setText("No hallucination analysis available");
                 txtHallucinationReason.setText("No analysis data available for this experiment.");
                 lblDetailHallucinate.setText("🔍 Hallucination: N/A");
                 lblDetailHallucinate.setForeground(Color.GRAY);
             }

         } catch (Exception e) {
             System.err.println("Error parsing details: " + e.getMessage());
             e.printStackTrace();
             clearDetailsPanel();
         }
     } else {
         System.err.println("Error fetching details: " + response);
         clearDetailsPanel();
     }
 }

    // Helper class for matched pairs
    private static class MatchedPair {
        String llmName;
        String gtName;
        String matchType;
    }

    private String padRight(String text, int length) {
        if (text == null) text = "";
        if (text.length() >= length) return text.substring(0, length);
        return text + " ".repeat(length - text.length());
    }

    private void clearDetailsPanel() {
        if (lblDetailId == null) return;
        lblDetailId.setText("Transcript ID: --");
        lblDetailName.setText("File: Select an execution cell...");
        lblDetailStatus.setText("Status: --");
        lblDetailHallucinate.setText("🔍 Hallucination: --");
        lblDetailHallucinate.setForeground(Color.GRAY);
        txtLlmNutrition.setText("Select a model cell to view...");
        txtCombinedIngredients.setText("Select a model cell to view analysis...");
        txtHallucinationReason.setText("Analysis will appear here when an experiment is selected...");
    }

    // ============================================================
    // TRANSCRIPT VIEWER
    // ============================================================

    private boolean isDialogOpen = false;
    private int lastSelectedTranscriptId = -1;

    private void showTranscriptContentDialog(int transcriptId, String fileName) {
        if (isDialogOpen) return;
        isDialogOpen = true;

        JDialog dialog = new JDialog(this, "Transcript Viewer: " + fileName, true);
        dialog.setSize(600, 450);
        dialog.setLocationRelativeTo(this);

        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                isDialogOpen = false;
                matrixTable.clearSelection();
                clearDetailsPanel();
            }
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                isDialogOpen = false;
                matrixTable.clearSelection();
                clearDetailsPanel();
            }
        });

        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        String response = sendRequest("GET_TRANSCRIPT", String.valueOf(transcriptId));

        if (response.startsWith("SUCCESS|")) {
            try {
                String jsonData = response.substring(8);
                JsonNode result = mapper.readTree(jsonData);
                if (result.has("content") && !result.get("content").isNull()) {
                    area.setText(result.get("content").asText());
                } else {
                    area.setText("No content available for this transcript.");
                }
            } catch (Exception e) {
                area.setText("Error parsing transcript: " + e.getMessage());
            }
        } else {
            area.setText("Error: " + response);
        }

        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    // ============================================================
    // TABLE SELECTION HANDLER
    // ============================================================

    private void onTableSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;

        int selectedRow = matrixTable.getSelectedRow();
        int selectedCol = matrixTable.getSelectedColumn();

        if (selectedRow == -1) return;

        // Transcript File column - show content dialog
        if (selectedCol == 1) {
            if (isDialogOpen) return;

            int transcriptId = (int) matrixTable.getValueAt(selectedRow, 0);
            String fileName = (String) matrixTable.getValueAt(selectedRow, 1);
            lastSelectedTranscriptId = transcriptId;
            showTranscriptContentDialog(transcriptId, fileName);
            return;
        }

        // Model columns - show details
        if (selectedCol >= 2) {
            try {
                int transcriptId = (int) matrixTable.getValueAt(selectedRow, 0);
                String modelHeader = matrixTable.getColumnName(selectedCol);
                String activePrompt = matrixPromptFilter.getSelectedItem().toString();
                fetchAndDisplaySchemaDetails(transcriptId, modelHeader, activePrompt);
            } catch (Exception ex) {
                clearDetailsPanel();
            }
        }
    }

    // ============================================================
    // EXPORT TO CSV
    // ============================================================

    private void exportToCSV() {
        String response = sendRequest("GET_LAYERS", "");
        
        List<String> layers = new ArrayList<>();
        if (response.startsWith("SUCCESS|")) {
            try {
                String jsonData = response.substring(8);
                JsonNode result = mapper.readTree(jsonData);
                JsonNode layersArray = result.get("layers");
                if (layersArray != null && layersArray.isArray()) {
                    for (JsonNode layer : layersArray) {
                        layers.add(layer.asText());
                    }
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error fetching layers: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            JOptionPane.showMessageDialog(this, "Failed to get layers: " + response, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (layers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No evaluation layers available.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String selectedLayer = (String) JOptionPane.showInputDialog(
            this,
            "Select the evaluation layer dataset to export:",
            "MasakGram Single Layer CSV Export",
            JOptionPane.QUESTION_MESSAGE,
            null,
            layers.toArray(),
            layers.get(0)
        );

        if (selectedLayer == null) return;

        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setDialogTitle("Select Destination Folder for: " + selectedLayer);
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        int userSelection = folderChooser.showSaveDialog(this);
        if (userSelection != JFileChooser.APPROVE_OPTION) return;

        File targetDir = folderChooser.getSelectedFile();

        new Thread(() -> {
            try {
                String result = sendRequest("EXPORT_CSV", selectedLayer + "|" + targetDir.getAbsolutePath());
                
                SwingUtilities.invokeLater(() -> {
                    if (result.startsWith("SUCCESS|")) {
                        String filePath = result.substring(8);
                        JOptionPane.showMessageDialog(
                            this,
                            "Successfully exported layer dataset to:\n" + filePath,
                            "Export Complete",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    } else {
                        JOptionPane.showMessageDialog(
                            this,
                            "Export failed:\n" + result,
                            "Error",
                            JOptionPane.ERROR_MESSAGE
                        );
                    }
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    "Pipeline Export Failed:\n" + ex.getMessage(),
                    "Database Engine Error",
                    JOptionPane.ERROR_MESSAGE
                ));
            }
        }).start();
    }

    // ============================================================
    // CELL RENDERER
    // ============================================================

    private static class StatusCellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object val, boolean isSel, boolean hasFocus, int r, int c) {
            Component cell = super.getTableCellRendererComponent(table, val, isSel, hasFocus, r, c);
            if (val == null) return cell;
            String s = val.toString();
            if (s.startsWith("COMPLETED")) {
                cell.setForeground(new Color(40, 167, 69));
            } else if (s.startsWith("FAILED")) {
                cell.setForeground(Color.RED);
            } else if (s.startsWith("RUNNING")) {
                cell.setForeground(BLUE_ACCENT);
            } else {
                cell.setForeground(Color.GRAY);
            }
            return cell;
        }
    }

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            new MasakGramDashboard().setVisible(true);
        });
    }
}
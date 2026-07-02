package com.masakgram.dashboard;

import com.formdev.flatlaf.FlatLightLaf; 
import com.masakgram.db.DatabaseManager;
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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MasakGramDashboard extends JFrame {

    private static final long serialVersionUID = 1L;

    // Light Theme UI Palette
    private static final Color BG_DARK      = new Color(245, 247, 250); 
    private static final Color BG_SIDEBAR   = new Color(225, 230, 239); 
    private static final Color CARD_BG      = new Color(255, 255, 255); 
    private static final Color BLUE_ACCENT  = new Color(0, 123, 255);   
    private static final Color TEXT_LIGHT   = new Color(33, 37, 41);    
    private static final Color BORDER_COLOR = new Color(210, 216, 225); 

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
    private JTextArea txtLlmIngredients;
    private JTextArea txtGtIngredients;

    private static final String[] MODELS = {"Llama 3.2 (3B)", "Phi-4-mini (3.8B)", "Qwen 2.5 (3B)", "Gemma-SEA-LION v4 (4B)", "MedGemma (4B)"};
    private static final String[] TECHNIQUES = {"zero-shot", "few-shot", "chain-of-thought", "structured-output"};

    public MasakGramDashboard() {
        setTitle("MasakGramPrompt - Experiment Execution Engine");
        setSize(1300, 850); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // =================================================================
        // SIDEBAR NAVIGATION
        // =================================================================
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

        // CHANGED: Status Matrix first, then Run Experiment
        createSidebarMenuButton(sidebar, "[=] Status Matrix", "MATRIX_PANEL");
        createSidebarMenuButton(sidebar, "[>] Run Experiment", "RUN_PANEL");
        
        // CSV Export Button - Relocated to align with sidebar actions
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

        mainContentCardPanel.add(buildRunExperimentPanel(), "RUN_PANEL");
        mainContentCardPanel.add(buildMatrixStatusPanel(), "MATRIX_PANEL");

        add(sidebar, BorderLayout.WEST);
        add(mainContentCardPanel, BorderLayout.CENTER);

        refreshAllData();
        startAutoRefresh();
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

    private void startAutoRefresh() {
        Timer timer = new Timer(3000, e -> {
            refreshMatrixStatusTable();
            refreshMetrics();
        });
        timer.start();
    }

    private void refreshAllData() {
        refreshMatrixStatusTable();
        refreshMetrics();
        clearDetailsPanel();
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
                try (
                    Socket socket = new Socket("localhost", 12345);
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                ) {
                    out.println("BATCH_EXECUTE|" + chosenModel + "|" + chosenTech);
                    String response;
                    while ((response = in.readLine()) != null) {
                        consoleArea.append(" " + response + "\n");
                        if (response.startsWith("STATUS|COMPLETED") || response.startsWith("ERROR")) break;
                    }
                } catch (Exception ex) {
                    consoleArea.append("\n[ERROR] Pipeline run failed: " + ex.getMessage() + "\n");
                } finally {
                    runBtn.setEnabled(true);
                    refreshAllData();
                }
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

        // Selector Bar Elements - Increased dropdown size
        JPanel selectorWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        selectorWrapper.setBackground(BG_DARK);
        JLabel filterLabel = new JLabel("Active Prompt Matrix:");
        filterLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        matrixPromptFilter = new JComboBox<>(TECHNIQUES);
        matrixPromptFilter.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        matrixPromptFilter.setPreferredSize(new Dimension(220, 36)); // Made box bigger
        matrixPromptFilter.addActionListener(e -> {
            refreshMatrixStatusTable();
            refreshMetrics();
        });
        
        selectorWrapper.add(filterLabel);
        selectorWrapper.add(matrixPromptFilter);
        headerBar.add(selectorWrapper, BorderLayout.EAST);
        topContainer.add(headerBar, BorderLayout.NORTH);

        // Unified Performance Ribbon Layout
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

        // Swapped "Verified By" with "Transcript File"
        String[] cols = {"ID", "Transcript File", "Llama 3.2", "Phi-4-mini", "Qwen 2.5", "Gemma-SEA", "MedGemma"};
        matrixTableModel = new DefaultTableModel(null, cols) {
            private static final long serialVersionUID = 1L;
            @Override public boolean isCellEditable(int r, int c) { return false; }
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

        JPanel detailWorkspacePanel = buildTranscriptDetailPanel();
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

    private JPanel buildTranscriptDetailPanel() {
        JPanel detailPanel = new JPanel(new BorderLayout(10, 10));
        detailPanel.setBackground(CARD_BG);
        detailPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_COLOR, 1),
            BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JPanel headerMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 25, 5));
        headerMeta.setBackground(CARD_BG);

        lblDetailId = new JLabel("Transcript ID: --");
        lblDetailId.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblDetailName = new JLabel("File Name: Select an execution cell...");
        lblDetailName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblDetailStatus = new JLabel("Status: --");
        lblDetailStatus.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblDetailHallucinate = new JLabel("HALLUCINATION DETECTED: --");
        lblDetailHallucinate.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblDetailHallucinate.setForeground(Color.GRAY);

        headerMeta.add(lblDetailId);
        headerMeta.add(lblDetailName);
        headerMeta.add(lblDetailStatus);
        headerMeta.add(lblDetailHallucinate);
        detailPanel.add(headerMeta, BorderLayout.NORTH);

        JPanel bodyGrid = new JPanel(new GridLayout(1, 3, 12, 0));
        bodyGrid.setBackground(CARD_BG);

        txtLlmNutrition = createDetailsTextArea();
        txtLlmIngredients = createDetailsTextArea();
        txtGtIngredients = createDetailsTextArea();

        bodyGrid.add(createTitledPanelWrapper(new JScrollPane(txtLlmNutrition), "LLM Nutrition Summary Output"));
        bodyGrid.add(createTitledPanelWrapper(new JScrollPane(txtLlmIngredients), "LLM Extracted Ingredients"));
        bodyGrid.add(createTitledPanelWrapper(new JScrollPane(txtGtIngredients), "Human Ground Truth Reference"));

        detailPanel.add(bodyGrid, BorderLayout.CENTER);
        return detailPanel;
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

    private boolean isDialogOpen = false;
    private int lastSelectedTranscriptId = -1; // Track the last selected transcript

    private void onTableSelectionChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        
        int selectedRow = matrixTable.getSelectedRow();
        int selectedCol = matrixTable.getSelectedColumn();
        
        if (selectedRow == -1) return;

        // If clicked on column 1 ("Transcript File"), launch popup displaying full content
        if (selectedCol == 1) {
            // Prevent multiple dialog windows from opening
            if (isDialogOpen) {
                return;
            }
            
            int transcriptId = (int) matrixTable.getValueAt(selectedRow, 0);
            String fileName = (String) matrixTable.getValueAt(selectedRow, 1);
            
            // Store the selected transcript ID
            lastSelectedTranscriptId = transcriptId;
            
            // Show the dialog
            showTranscriptContentDialog(transcriptId, fileName);
            return;
        }

        // For other columns (2+), update the details panel
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

    // Displays content window when Transcript File layout is clicked
    private void showTranscriptContentDialog(int transcriptId, String fileName) {
        // Prevent multiple dialog windows
        if (isDialogOpen) {
            return;
        }
        isDialogOpen = true;
        
        JDialog dialog = new JDialog(this, "Transcript Viewer: " + fileName, true);
        dialog.setSize(600, 450);
        dialog.setLocationRelativeTo(this);
        
        // Reset the flag and clear selection when dialog is closed
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent windowEvent) {
                isDialogOpen = false;
                // Clear the selection after dialog closes
                matrixTable.clearSelection();
                // Also clear the details panel
                clearDetailsPanel();
            }
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                isDialogOpen = false;
                // Clear the selection after dialog closes
                matrixTable.clearSelection();
                // Also clear the details panel
                clearDetailsPanel();
            }
        });
        
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Get the file path from the database
        String sql = "SELECT file_path FROM transcript WHERE transcript_id = ?";
        String filePath = null;
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    filePath = rs.getString("file_path");
                }
            }
        } catch (Exception ex) {
            area.setText("Error fetching file path from database:\n" + ex.getMessage());
            dialog.add(new JScrollPane(area), BorderLayout.CENTER);
            dialog.setVisible(true);
            return;
        }

        // If file path is null or empty, show error
        if (filePath == null || filePath.isEmpty()) {
            area.setText("No file path associated with this transcript.\n\n" +
                       "Transcript ID: " + transcriptId + "\n" +
                       "File Name: " + fileName);
            dialog.add(new JScrollPane(area), BorderLayout.CENTER);
            dialog.setVisible(true);
            return;
        }

        // Try to read the file content
        File transcriptFile = new File(filePath);
        if (!transcriptFile.exists()) {
            area.setText("Transcript file not found at:\n" + filePath + "\n\n" +
                       "Please verify the file exists in the specified location.\n\n" +
                       "Transcript ID: " + transcriptId + "\n" +
                       "File Name: " + fileName);
            dialog.add(new JScrollPane(area), BorderLayout.CENTER);
            dialog.setVisible(true);
            return;
        }

        // Read the file content
        try (BufferedReader reader = new BufferedReader(new java.io.FileReader(transcriptFile))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            area.setText(content.toString());
        } catch (Exception ex) {
            area.setText("Error reading transcript file:\n" + ex.getMessage() + "\n\n" +
                       "File Path: " + filePath + "\n" +
                       "Transcript ID: " + transcriptId + "\n" +
                       "File Name: " + fileName);
        }

        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.setVisible(true);
    }

    private void fetchAndDisplaySchemaDetails(int transcriptId, String modelHeaderName, String techniqueName) {
        clearDetailsPanel();

        String transcriptSql = "SELECT verified_by_name FROM transcript WHERE transcript_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(transcriptSql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    lblDetailId.setText("Transcript ID: " + transcriptId);
                    lblDetailName.setText("File Name: " + rs.getString("verified_by_name"));
                }
            }
        } catch (Exception ex) {
            System.err.println("Transcript Query Error: " + ex.getMessage());
        }

        String experimentSql = "SELECT e.status, nr.result_id, nr.recipe_name, nr.servings_estimated, "
                             + "       nr.total_calories, nr.total_protein_g, nr.total_carbohydrate_g, nr.total_fat_g "
                             + "FROM experiment e "
                             + "INNER JOIN llm_model m ON e.model_id = m.model_id "
                             + "INNER JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
                             + "LEFT JOIN nutrition_result nr ON e.experiment_id = nr.experiment_id "
                             + "WHERE e.transcript_id = ? AND m.model_name LIKE ? AND pt.technique_name = ?";

        int resolvedResultId = -1;
        List<String> llmIngredientsList = new ArrayList<>();
        List<String> groundTruthIngredientsList = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(experimentSql)) {
            
            pstmt.setInt(1, transcriptId);
            pstmt.setString(2, "%" + modelHeaderName.substring(0, Math.min(modelHeaderName.length(), 8)) + "%");
            pstmt.setString(3, techniqueName);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    String status = rs.getString("status");
                    lblDetailStatus.setText("Status: " + (status != null ? status.toUpperCase() : "PENDING"));
                    resolvedResultId = rs.getInt("result_id");
                    
                    if (status != null && status.equalsIgnoreCase("completed") && resolvedResultId > 0) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Recipe extracted: ").append(rs.getString("recipe_name")).append("\n");
                        sb.append("Est. Servings: ").append(rs.getInt("servings_estimated")).append("\n\n");
                        sb.append("Totals Summary:\n");
                        sb.append(" • Calories: ").append(rs.getFloat("total_calories")).append(" kcal\n");
                        sb.append(" • Protein: ").append(rs.getFloat("total_protein_g")).append(" g\n");
                        sb.append(" • Carbs: ").append(rs.getFloat("total_carbohydrate_g")).append(" g\n");
                        sb.append(" • Fat: ").append(rs.getFloat("total_fat_g")).append(" g");
                        txtLlmNutrition.setText(sb.toString());
                    } else {
                        txtLlmNutrition.setText("No parsed analytical metrics matching selection context.");
                    }
                } else {
                    lblDetailStatus.setText("Status: UNEXECUTED");
                    txtLlmNutrition.setText("Experiment not yet executed for this condition matrix cross-tabulation.");
                }
            }
        } catch (Exception ex) {
            System.err.println("Experiment Context Verification Drop Failure: " + ex.getMessage());
        }

        if (resolvedResultId > 0) {
            String llmIngSql = "SELECT name_original, quantity_value, unit_original FROM ingredient_result WHERE result_id = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(llmIngSql)) {
                pstmt.setInt(1, resolvedResultId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) {
                        String name = rs.getString("name_original");
                        float qty = rs.getFloat("quantity_value");
                        String unit = rs.getString("unit_original");
                        
                        String fullLine = String.format("• %s (%.1f %s)", name, qty, (unit != null ? unit : ""));
                        sb.append(fullLine).append("\n");
                        if (name != null) llmIngredientsList.add(name.trim().toLowerCase());
                    }
                    txtLlmIngredients.setText(sb.length() > 0 ? sb.toString() : "No mapped item outputs extracted.");
                }
            } catch (Exception ex) {
                System.err.println("LLM Ingredient Parse Error: " + ex.getMessage());
            }
        } else {
            txtLlmIngredients.setText("---");
        }

        String gtIngSql = "SELECT gti.name_original, gti.quantity_expression "
                        + "FROM ground_truth_ingredient gti "
                        + "INNER JOIN ground_truth_reel gtr ON gti.gt_reel_id = gtr.gt_reel_id "
                        + "WHERE gtr.transcript_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(gtIngSql)) {
            pstmt.setInt(1, transcriptId);
            try (ResultSet rs = pstmt.executeQuery()) {
                StringBuilder sb = new StringBuilder();
                while (rs.next()) {
                    String name = rs.getString("name_original");
                    String expr = rs.getString("quantity_expression");
                    
                    sb.append("• ").append(name).append(" [").append(expr != null ? expr : "").append("]\n");
                    if (name != null) groundTruthIngredientsList.add(name.trim().toLowerCase());
                }
                txtGtIngredients.setText(sb.length() > 0 ? sb.toString() : "No baseline reference text provided.");
            }
        } catch (Exception ex) {
            System.err.println("Ground Truth Context Verification Failure: " + ex.getMessage());
        }

        if (llmIngredientsList.isEmpty() || groundTruthIngredientsList.isEmpty()) {
            lblDetailHallucinate.setText("HALLUCINATION DETECTED: N/A");
            lblDetailHallucinate.setForeground(Color.GRAY);
        } else {
            boolean hallucinated = false;
            for (String llmIng : llmIngredientsList) {
                boolean matched = false;
                for (String gtIng : groundTruthIngredientsList) {
                    if (gtIng.contains(llmIng) || llmIng.contains(gtIng)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    hallucinated = true;
                    break;
                }
            }

            if (hallucinated) {
                lblDetailHallucinate.setText("HALLUCINATION DETECTED: YES");
                lblDetailHallucinate.setForeground(Color.RED);
            } else {
                lblDetailHallucinate.setText("HALLUCINATION DETECTED: NO");
                lblDetailHallucinate.setForeground(new Color(40, 167, 69));
            }
        }
    }

    private void clearDetailsPanel() {
        if (lblDetailId == null) return;
        lblDetailId.setText("Transcript ID: --");
        lblDetailName.setText("File Name: Select an execution cell...");
        lblDetailStatus.setText("Status: --");
        lblDetailHallucinate.setText("HALLUCINATION DETECTED: --");
        lblDetailHallucinate.setForeground(Color.GRAY);
        txtLlmNutrition.setText("");
        txtLlmIngredients.setText("");
        txtGtIngredients.setText("");
    }

    private void refreshMatrixStatusTable() {
        if (matrixTableModel == null) return;
        
        String selectedPrompt = matrixPromptFilter.getSelectedItem().toString();
        int selectedRow = matrixTable.getSelectedRow();
        int selectedCol = matrixTable.getSelectedColumn();
        matrixTableModel.setRowCount(0);

        // Updated query to pull 'file_name' instead of 'verified_by_name'
        String sql = "SELECT t.transcript_id, "
                   + "       COALESCE(t.file_name, 'No Name') AS transcript_file, "
                   + "       m.model_name, "
                   + "       e.status, "
                   + "       e.experiment_id "
                   + "FROM transcript t "
                   + "LEFT JOIN experiment e ON t.transcript_id = e.transcript_id "
                   + "     AND e.technique_id = (SELECT technique_id FROM prompt_technique WHERE technique_name = ? LIMIT 1) "
                   + "LEFT JOIN llm_model m ON e.model_id = m.model_id "
                   + "ORDER BY t.transcript_id ASC";

        Map<Integer, Object[]> matrixRowsMap = new LinkedHashMap<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, selectedPrompt);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("transcript_id");
                    String fileTranscript = rs.getString("transcript_file");
                    String model = rs.getString("model_name");
                    String status = rs.getString("status");
                    int expId = rs.getInt("experiment_id");
                    boolean hasExpId = !rs.wasNull();

                    if (!matrixRowsMap.containsKey(id)) {
                        matrixRowsMap.put(id, new Object[]{id, fileTranscript, "-", "-", "-", "-", "-"});
                    }

                    if (model != null && status != null) {
                        Object[] rowData = matrixRowsMap.get(id);
                        int colIndex = getModelColumnIndex(model);
                        if (colIndex != -1) {
                            String displayValue = status.toUpperCase();
                            if (hasExpId) {
                                displayValue += " (#" + expId + ")";
                            }
                            rowData[colIndex] = displayValue;
                        }
                    }
                }
            }

            for (Object[] row : matrixRowsMap.values()) {
                matrixTableModel.addRow(row);
            }
            
            if (selectedRow != -1 && selectedRow < matrixTable.getRowCount()) {
                matrixTable.setRowSelectionInterval(selectedRow, selectedRow);
                if (selectedCol != -1 && selectedCol < matrixTable.getColumnCount()) {
                    matrixTable.setColumnSelectionInterval(selectedCol, selectedCol);
                }
            }
        } catch (Exception ex) {
            System.err.println("Matrix Table Data Pivot Error: " + ex.getMessage());
        }
    }

    private int getModelColumnIndex(String modelName) {
        if (modelName.contains("Llama 3.2")) return 2;
        if (modelName.contains("Phi-4")) return 3;
        if (modelName.contains("Qwen 2.5")) return 4;
        if (modelName.contains("Gemma-SEA")) return 5;
        if (modelName.contains("MedGemma")) return 6;
        return -1;
    }

    // Finished implementation tracking technique-specific success rates dynamically 
 // Finished implementation tracking technique-specific success rates dynamically 
    private void refreshMetrics() {
        if (lblStatTranscripts == null || matrixPromptFilter == null) return;
        
        String selectedTechnique = matrixPromptFilter.getSelectedItem().toString();

        String countTranscriptsSql = "SELECT COUNT(*) AS total FROM transcript";
        // CHANGED: Show ALL experiments, not filtered by technique
        String runningExperimentsSql = "SELECT COUNT(*) AS total FROM experiment";
        String rateSql = "SELECT "
                       + "  SUM(CASE WHEN LOWER(e.status) = 'completed' THEN 1 ELSE 0 END) as success_count, "
                       + "  SUM(CASE WHEN LOWER(e.status) = 'failed' THEN 1 ELSE 0 END) as failure_count "
                       + "FROM experiment e "
                       + "INNER JOIN prompt_technique pt ON e.technique_id = pt.technique_id "
                       + "WHERE pt.technique_name = ?";

        try (Connection conn = DatabaseManager.getConnection()) {
            // 1. Total Transcripts
            try (PreparedStatement pst = conn.prepareStatement(countTranscriptsSql);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) lblStatTranscripts.setText(String.valueOf(rs.getInt("total")));
            }
            
            // 2. ALL Experiments Count (not filtered)
            try (PreparedStatement pst = conn.prepareStatement(runningExperimentsSql);
                 ResultSet rs = pst.executeQuery()) {
                if (rs.next()) lblStatExperiments.setText(String.valueOf(rs.getInt("total")));
            }

            // 3. Dynamic Technique Specific Success/Failure calculation (percentage only)
            try (PreparedStatement pst = conn.prepareStatement(rateSql)) {
                pst.setString(1, selectedTechnique);
                try (ResultSet rs = pst.executeQuery()) {
                    if (rs.next()) {
                        int success = rs.getInt("success_count");
                        int failure = rs.getInt("failure_count");
                        int total = success + failure;
                        
                        if (total > 0) {
                            double successPct = ((double) success / total) * 100;
                            double failurePct = ((double) failure / total) * 100;
                            lblStatSuccessFailure.setText(String.format("%.1f%% / %.1f%%", successPct, failurePct));
                        } else {
                            lblStatSuccessFailure.setText("0.0% / 0.0%");
                        }
                    }
                }
            }
        } catch (Exception ex) {
            System.err.println("Metrics Load Error: " + ex.getMessage());
        }
    }

 // Replace the old exportToCSV() method at the bottom of your MasakGramDashboard class with this:

    private void exportToCSV() {
        // 1. Get the list of available evaluation layers from the service
        java.util.List<String> layers = CSVExporterService.getAvailableLayers();
        if (layers == null || layers.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No evaluation layers found in the export engine.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. Prompt the user to select exactly which layer they want to export
        String selectedLayer = (String) JOptionPane.showInputDialog(
            this,
            "Select the evaluation layer dataset to export:",
            "MasakGram Single Layer CSV Export",
            JOptionPane.QUESTION_MESSAGE,
            null,
            layers.toArray(),
            layers.get(0)
        );

        // If the user cancels or closes the dialog, stop execution
        if (selectedLayer == null) return;

        // 3. Select the Target Directory where the file should be saved
        JFileChooser folderChooser = new JFileChooser();
        folderChooser.setDialogTitle("Select Destination Folder for: " + selectedLayer);
        folderChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int userSelection = folderChooser.showSaveDialog(this);
        if (userSelection != JFileChooser.APPROVE_OPTION) return;
        
        File targetDir = folderChooser.getSelectedFile();

        // 4. Execute the targeted database query in a background thread to keep the UI fluid
        new Thread(() -> {
            try {
                CSVExporterService.exportSingleLayer(selectedLayer, targetDir);
                
                // Notify user of success on the Event Dispatch Thread (EDT)
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    this,
                    "Successfully exported layer dataset to:\n" + new File(targetDir, selectedLayer).getAbsolutePath(),
                    "Export Complete",
                    JOptionPane.INFORMATION_MESSAGE
                ));
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FlatLightLaf.setup();
            new MasakGramDashboard().setVisible(true);
        });
    }
}
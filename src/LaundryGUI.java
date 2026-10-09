import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.PrintStream;

/**
 * Simple visual interface for the Smart Laundry Facility Simulation.
 *
 * The simulation logic remains in LaundrySimulation.java.
 * This class only displays the live state and simulation log.
 *
 * Compile:
 *   javac LaundrySimulation.java LaundryGUI.java
 *
 * Run:
 *   java LaundryGUI
 */
public class LaundryGUI extends JFrame {

    // ---------- Simple colours ----------
    private static final Color BG = new Color(245, 247, 250);
    private static final Color CARD = Color.WHITE;
    private static final Color TEXT = new Color(35, 40, 48);
    private static final Color MUTED = new Color(100, 110, 125);
    private static final Color WASH = new Color(52, 152, 219);
    private static final Color DRY = new Color(230, 145, 40);
    private static final Color PAY = new Color(39, 174, 96);
    private static final Color FAIL = new Color(220, 70, 70);
    private static final Color FREE = new Color(220, 225, 232);

    // ---------- UI ----------
    private JLabel elapsedLabel;
    private JLabel statusLabel;

    private JLabel arrivedValue;
    private JLabel completedValue;
    private JLabel failedValue;

    private JLabel washingValue;
    private JLabel dryingValue;
    private JLabel payingValue;

    private JLabel washerFailValue;
    private JLabel kioskFailValue;

    private JLabel washerUsageLabel;
    private JLabel dryerUsageLabel;
    private JLabel kioskUsageLabel;

    private JProgressBar washerBar;
    private JProgressBar dryerBar;
    private JProgressBar kioskBar;

    private JTextArea logArea;

    private Timer refreshTimer;
    private long guiStartMs;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            LaundryGUI gui = new LaundryGUI();
            gui.setVisible(true);
            gui.startSimulation();
        });
    }

    public LaundryGUI() {
        super("Smart Laundry Facility Simulation");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(900, 650));
        setSize(1000, 720);
        setLocationRelativeTo(null);

        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(12, 12));

        add(createHeader(), BorderLayout.NORTH);
        add(createMainContent(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);
    }

    // ================================================================
    // HEADER
    // ================================================================

    private JPanel createHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(15, 18, 0, 18));

        JPanel titlePanel = new JPanel();
        titlePanel.setLayout(new BoxLayout(titlePanel, BoxLayout.Y_AXIS));
        titlePanel.setOpaque(false);

        JLabel title = new JLabel("Smart Laundry Facility");
        title.setFont(new Font("SansSerif", Font.BOLD, 24));
        title.setForeground(TEXT);

        JLabel subtitle = new JLabel(
                "CT074-3-2  |  50 customers  |  6 washers  |  4 dryers  |  2 kiosks");
        subtitle.setFont(new Font("SansSerif", Font.PLAIN, 13));
        subtitle.setForeground(MUTED);

        titlePanel.add(title);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(subtitle);

        elapsedLabel = new JLabel("00:00");
        elapsedLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        elapsedLabel.setForeground(TEXT);

        panel.add(titlePanel, BorderLayout.WEST);
        panel.add(elapsedLabel, BorderLayout.EAST);

        return panel;
    }

    // ================================================================
    // MAIN CONTENT
    // ================================================================

    private JPanel createMainContent() {
        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setOpaque(false);
        main.setBorder(new EmptyBorder(0, 18, 0, 18));

        main.add(createTopCards(), BorderLayout.NORTH);

        JPanel lower = new JPanel(new GridLayout(1, 2, 12, 0));
        lower.setOpaque(false);

        lower.add(createResourcePanel());
        lower.add(createLogPanel());

        main.add(lower, BorderLayout.CENTER);

        return main;
    }

    // ================================================================
    // SUMMARY CARDS
    // ================================================================

    private JPanel createTopCards() {
        JPanel panel = new JPanel(new GridLayout(2, 3, 10, 10));
        panel.setOpaque(false);

        arrivedValue = addCard(panel, "Customers Arrived", "0", TEXT);
        completedValue = addCard(panel, "Completed", "0", PAY);
        failedValue = addCard(panel, "Failed", "0", FAIL);

        washingValue = addCard(panel, "Washing Now", "0", WASH);
        dryingValue = addCard(panel, "Drying Now", "0", DRY);
        payingValue = addCard(panel, "Paying Now", "0", PAY);

        return panel;
    }

    private JLabel addCard(JPanel parent, String title, String value, Color valueColor) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 228, 233)),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        titleLabel.setForeground(MUTED);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 24));
        valueLabel.setForeground(valueColor);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        card.add(titleLabel, BorderLayout.WEST);
        card.add(valueLabel, BorderLayout.EAST);

        parent.add(card);
        return valueLabel;
    }

    // ================================================================
    // RESOURCE PANEL
    // ================================================================

    private JPanel createResourcePanel() {
        JPanel outer = cardPanel();
        outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));

        outer.add(sectionTitle("RESOURCE STATUS"));
        outer.add(Box.createVerticalStrut(10));

        outer.add(resourceRow(
                "Washing Machines", "6 machines", WASH,
                () -> washerUsageLabel,
                () -> washerBar
        ));

        outer.add(Box.createVerticalStrut(12));

        outer.add(resourceRow(
                "Dryers", "4 machines", DRY,
                () -> dryerUsageLabel,
                () -> dryerBar
        ));

        outer.add(Box.createVerticalStrut(12));

        outer.add(resourceRow(
                "Payment Kiosks", "2 kiosks", PAY,
                () -> kioskUsageLabel,
                () -> kioskBar
        ));

        outer.add(Box.createVerticalStrut(16));

        JLabel failureTitle = sectionTitle("FAILURES");
        outer.add(failureTitle);
        outer.add(Box.createVerticalStrut(8));

        JPanel failureRow = new JPanel(new GridLayout(1, 2, 8, 0));
        failureRow.setOpaque(false);

        washerFailValue = smallValuePanel(failureRow, "Washer failures");
        kioskFailValue = smallValuePanel(failureRow, "Kiosk failures");

        outer.add(failureRow);
        outer.add(Box.createVerticalGlue());

        return outer;
    }

    private JPanel resourceRow(
            String name,
            String capacityText,
            Color colour,
            java.util.function.Supplier<JLabel> labelSupplier,
            java.util.function.Supplier<JProgressBar> barSupplier) {

        JPanel row = new JPanel(new BorderLayout(6, 5));
        row.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
        nameLabel.setForeground(TEXT);

        JLabel capacity = new JLabel(capacityText);
        capacity.setFont(new Font("SansSerif", Font.PLAIN, 11));
        capacity.setForeground(MUTED);

        top.add(nameLabel, BorderLayout.WEST);
        top.add(capacity, BorderLayout.EAST);

        JProgressBar bar = new JProgressBar(0, 100);
        bar.setStringPainted(false);
        bar.setPreferredSize(new Dimension(0, 16));
        bar.setForeground(colour);
        bar.setBackground(FREE);

        JLabel usage = new JLabel("0 in use");
        usage.setFont(new Font("SansSerif", Font.PLAIN, 12));
        usage.setForeground(MUTED);

        // Store references in the appropriate fields.
        if (name.startsWith("Washing")) {
            washerBar = bar;
            washerUsageLabel = usage;
        } else if (name.startsWith("Dryer")) {
            dryerBar = bar;
            dryerUsageLabel = usage;
        } else {
            kioskBar = bar;
            kioskUsageLabel = usage;
        }

        row.add(top, BorderLayout.NORTH);
        row.add(bar, BorderLayout.CENTER);
        row.add(usage, BorderLayout.SOUTH);

        return row;
    }

    private JLabel smallValuePanel(JPanel parent, String title) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(248, 249, 251));
        panel.setBorder(BorderFactory.createLineBorder(new Color(230, 233, 237)));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        titleLabel.setForeground(MUTED);

        JLabel valueLabel = new JLabel("0");
        valueLabel.setFont(new Font("SansSerif", Font.BOLD, 18));
        valueLabel.setForeground(FAIL);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        panel.add(titleLabel, BorderLayout.WEST);
        panel.add(valueLabel, BorderLayout.EAST);

        parent.add(panel);
        return valueLabel;
    }

    // ================================================================
    // LOG
    // ================================================================

    private JPanel createLogPanel() {
        JPanel outer = cardPanel();
        outer.setLayout(new BorderLayout(0, 8));

        outer.add(sectionTitle("SIMULATION LOG"), BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        logArea.setBackground(new Color(32, 36, 43));
        logArea.setForeground(new Color(235, 238, 242));
        logArea.setMargin(new Insets(8, 8, 8, 8));
        logArea.setLineWrap(false);

        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(215, 218, 223)));

        outer.add(scroll, BorderLayout.CENTER);

        return outer;
    }

    // ================================================================
    // STATUS BAR
    // ================================================================

    private JPanel createStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(8, 18, 10, 18));

        statusLabel = new JLabel("● Ready");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusLabel.setForeground(MUTED);

        JLabel info = new JLabel(
                "Green = normal   |   Red = failure/congestion   |   Progress bars = resources in use");
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        info.setForeground(MUTED);

        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(info, BorderLayout.EAST);

        return panel;
    }

    // ================================================================
    // START SIMULATION
    // ================================================================

    private void startSimulation() {
        guiStartMs = System.currentTimeMillis();

        /*
         * The existing simulation writes its log through System.out.
         * We keep the original console output and also copy each complete
         * println() line into this GUI.
         */
        PrintStream originalOut = System.out;

        System.setOut(new PrintStream(originalOut) {
            @Override
            public void println(String x) {
                originalOut.println(x);
                appendLog(x);
            }
        });

        statusLabel.setText("● Simulation running...");
        statusLabel.setForeground(new Color(200, 140, 20));

        refreshTimer = new Timer(200, e -> refreshDisplay());
        refreshTimer.start();

        Thread simulationThread = new Thread(() -> {
            try {
                LaundrySimulation.runSimulation();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                appendLog("Simulation interrupted.");
            } catch (Exception ex) {
                appendLog("ERROR: " + ex.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    if (refreshTimer != null) {
                        refreshTimer.stop();
                    }

                    refreshDisplay();

                    int served = LaundrySimulation.customersServed.get();
                    int failed = LaundrySimulation.customersFailed.get();

                    statusLabel.setText(
                            "● Simulation complete — " + served +
                            " completed, " + failed + " failed"
                    );
                    statusLabel.setForeground(PAY);
                });
            }
        });

        simulationThread.setName("Laundry-Simulation");
        simulationThread.setDaemon(true);
        simulationThread.start();
    }

    // ================================================================
    // LIVE DISPLAY
    // ================================================================

    private void refreshDisplay() {
        long elapsed = System.currentTimeMillis() - guiStartMs;
        elapsedLabel.setText(String.format(
                "%02d:%02d", elapsed / 60000, (elapsed / 1000) % 60
        ));

        int washers = LaundrySimulation.inUseWashers.get();
        int dryers = LaundrySimulation.inUseDryers.get();
        int kiosks = LaundrySimulation.inUseKiosks.get();

        int served = LaundrySimulation.customersServed.get();
        int failed = LaundrySimulation.customersFailed.get();

        arrivedValue.setText(String.valueOf(served + failed));
        completedValue.setText(String.valueOf(served));
        failedValue.setText(String.valueOf(failed));

        washingValue.setText(String.valueOf(washers));
        dryingValue.setText(String.valueOf(dryers));
        payingValue.setText(String.valueOf(kiosks));

        washerFailValue.setText(
                String.valueOf(LaundrySimulation.washerFailCount.get())
        );
        kioskFailValue.setText(
                String.valueOf(LaundrySimulation.kioskFailCount.get())
        );

        updateResource(washerBar, washerUsageLabel, washers, 6);
        updateResource(dryerBar, dryerUsageLabel, dryers, 4);
        updateResource(kioskBar, kioskUsageLabel, kiosks, 2);

        if (LaundrySimulation.congestionActive) {
            statusLabel.setText(
                    "⚠ CONGESTION — kiosks are failing; owner recovery active"
            );
            statusLabel.setForeground(FAIL);
        }
    }

    private void updateResource(
            JProgressBar bar,
            JLabel label,
            int used,
            int capacity) {

        int percent = (int) Math.round((used * 100.0) / capacity);
        bar.setValue(percent);
        label.setText(used + " / " + capacity + " in use");
    }

    // ================================================================
    // HELPERS
    // ================================================================

    private JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(225, 228, 233)),
                new EmptyBorder(14, 14, 14, 14)
        ));
        return panel;
    }

    private JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(MUTED);
        return label;
    }

    private void appendLog(String line) {
        SwingUtilities.invokeLater(() -> {
            if (logArea == null) {
                return;
            }

            logArea.append(line + "\n");

            // Keep the interface responsive during long runs.
            if (logArea.getLineCount() > 500) {
                try {
                    int end = logArea.getLineEndOffset(100);
                    logArea.getDocument().remove(0, end);
                } catch (Exception ignored) {
                    // Safe UI cleanup only.
                }
            }

            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }
}

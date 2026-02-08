package jconnect.ui;

import javax.swing.*;
import java.awt.*;

public class App extends JFrame {

    // UI Components (Declared as class variables so you can access them easily)
    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JLabel connectionStatusLabel;

    public App() {
        // 1. Main Window Setup
        setTitle("JConnect");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // Center on screen
        setLayout(new BorderLayout(10, 10)); // BorderLayout with 10px gaps

        // 2. Initialize Panels
        initTopPanel();
        initLeftPanel();
        initRightPanel();

        // 3. Add visible check (Optional, mainly for testing)
        setVisible(true);
    }

    private void initTopPanel() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        connectionStatusLabel = new JLabel("Connected to: None");
        connectionStatusLabel.setFont(new Font("Arial", Font.BOLD, 14));

        topPanel.add(connectionStatusLabel);

        // Add to the top (North) of the main frame
        add(topPanel, BorderLayout.NORTH);
    }

    private void initLeftPanel() {
        // Create the list model and list
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);

        // Add some dummy data for visualization
        deviceListModel.addElement("Searching for devices...");

        // Wrap the list in a scroll pane
        JScrollPane scrollPane = new JScrollPane(deviceList);
        scrollPane.setPreferredSize(new Dimension(200, 0)); // Set fixed width
        scrollPane.setBorder(BorderFactory.createTitledBorder("Available Devices"));

        // Add to the left (West) of the main frame
        add(scrollPane, BorderLayout.WEST);
    }

    private void initRightPanel() {
        JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
        rightPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));

        // -- Chat Area (Center of Right Panel) --
        chatArea = new JTextArea();
        chatArea.setEditable(false); // User shouldn't type here directly
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(Color.GRAY));
        rightPanel.add(chatScroll, BorderLayout.CENTER);

        // -- Input Area (Bottom of Right Panel) --
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        messageField = new JTextField();
        sendButton = new JButton("Send");

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        rightPanel.add(inputPanel, BorderLayout.SOUTH);

        // Add to the center of the main frame
        add(rightPanel, BorderLayout.CENTER);
    }

    // Main method to run and test the UI immediately
    public static void main(String[] args) {
        // Run on the Event Dispatch Thread (Best practice for Swing)
        SwingUtilities.invokeLater(() -> {
            new App();
        });
    }

    // -- Helper methods for your logic to use later --

    public void updateConnectionStatus(String deviceName) {
        connectionStatusLabel.setText("Connected to: " + deviceName);
    }

    public void appendMessage(String sender, String message) {
        chatArea.append(sender + ": " + message + "\n");
        // Auto-scroll to bottom
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    public void addDeviceToList(String deviceName) {
        if (deviceListModel.contains("Searching for devices...")) {
            deviceListModel.clear();
        }
        deviceListModel.addElement(deviceName);
    }
}
package jconnect.ui;

import jconnect.network.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class App extends JFrame implements ConnectionObserver {
    // UI Components
    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    private JTextArea chatArea;
    private JTextField messageField;
    private JLabel connectionStatusLabel;
    private JButton sendButton;

    // Logic
    private NetworkManager networkManager;
    private String selectedIp;

    public App() {
        // 1. Window Setup
        setTitle("JConnect P2P Messenger");
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10)); // Gap between main panels

        // 2. Initialize Logic
        networkManager = new NetworkManager(this);

        // 3. Setup Panels
        initLeftPanel();
        initRightPanel();

        // 4. Start Logic
        networkManager.start();
        startDeviceDiscoveryTimer();

        setVisible(true);
    }

    private void initLeftPanel() {
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Listener to handle clicking a device
        deviceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String ip = deviceList.getSelectedValue();
                if (ip != null) {
                    selectedIp = ip;
                    connectionStatusLabel.setText("Chatting with: " + selectedIp);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(deviceList);
        scrollPane.setPreferredSize(new Dimension(200, 0)); // Fixed width for sidebar
        scrollPane.setBorder(BorderFactory.createTitledBorder("Online Devices"));

        // Add to main frame WEST
        add(scrollPane, BorderLayout.WEST);
    }

    private void initRightPanel() {
        // This panel holds the Top Status, Center Chat, and Bottom Input
        JPanel rightContainer = new JPanel(new BorderLayout(5, 5));

        // A. Top Panel (Status)
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        connectionStatusLabel = new JLabel("Select a device to start chatting");
        connectionStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        topPanel.add(connectionStatusLabel);
        topPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        // B. Center Panel (Chat Area)
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        // C. Bottom Panel (Input)
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding

        messageField = new JTextField();
        sendButton = new JButton("Send");

        // Action Listeners
        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage()); // Allow Enter key to send

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Assemble Right Container
        rightContainer.add(topPanel, BorderLayout.NORTH);
        rightContainer.add(chatScroll, BorderLayout.CENTER);
        rightContainer.add(inputPanel, BorderLayout.SOUTH);

        // Add this container to the main frame CENTER
        // This prevents the input field from stretching under the left panel
        add(rightContainer, BorderLayout.CENTER);
    }

    private void startDeviceDiscoveryTimer() {
        // Refresh Sidebar every 3 seconds without losing selection
        new Timer(3000, e -> {
            List<String> online = DeviceRegistry.getOnlineDevices();

            // 1. Save current selection
            String currentSelection = deviceList.getSelectedValue();

            // 2. Update model
            deviceListModel.clear();
            for (String ip : online) {
                deviceListModel.addElement(ip);
            }

            // 3. Restore selection if the device is still online
            if (currentSelection != null && deviceListModel.contains(currentSelection)) {
                deviceList.setSelectedValue(currentSelection, true);
            }
        }).start();
    }

    private void sendMessage() {
        String msg = messageField.getText();
        if (selectedIp != null && !msg.trim().isEmpty()) {
            networkManager.sendMessageTo(selectedIp, msg);
            appendChat("Me", msg);
            messageField.setText("");
        } else if (selectedIp == null) {
            JOptionPane.showMessageDialog(this, "Please select a device from the list first.");
        }
    }

    private void appendChat(String sender, String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(sender + ": " + msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength()); // Auto-scroll
        });
    }

    // --- ConnectionObserver Implementation ---

    @Override
    public void onMessage(String ip, String msg) {
        // Only show message if we are chatting with this IP, or maybe show notification
        // For now, we append everything, but you might want to filter by selectedIp later
        appendChat(ip, msg);
    }

    @Override
    public void onStatusChange(String ip, boolean online) {
        SwingUtilities.invokeLater(() -> {
            String status = online ? "joined" : "left";
            chatArea.append("[System]: " + ip + " " + status + "\n");
        });
    }

    public static void main(String[] args) {
        // Use system look and feel for better aesthetics
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}

        SwingUtilities.invokeLater(App::new);
    }
}
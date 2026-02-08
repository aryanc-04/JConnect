package jconnect.ui;

import com.formdev.flatlaf.FlatDarkLaf; // Import FlatLaf
import jconnect.network.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class App extends JFrame implements ConnectionObserver {
    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    private JTextArea chatArea;
    private JTextField messageField;
    private JLabel connectionStatusLabel;
    private JButton sendButton, sendFileButton;
    private JProgressBar progressBar;

    private NetworkManager networkManager;
    private String selectedIp;

    public App() {
        setTitle("JConnect P2P Messenger");
        setSize(950, 650); // Slightly larger for modern feel
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Use a container with padding for the main layout
        JPanel mainContainer = new JPanel(new BorderLayout(15, 15));
        mainContainer.setBorder(new EmptyBorder(15, 15, 15, 15)); // Global padding
        setContentPane(mainContainer);

        networkManager = new NetworkManager(this);
        initLeftPanel();
        initRightPanel();

        networkManager.start();
        startDeviceDiscoveryTimer();

        setVisible(true);
    }

    private void initLeftPanel() {
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        // Modern list styling
        deviceList.setFixedCellHeight(35);
        deviceList.setFont(new Font("Segoe UI", Font.PLAIN, 14));

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
        scrollPane.setPreferredSize(new Dimension(220, 0));
        // FlatLaf handles borders well, but TitledBorder can look dated.
        // Let's use a Label header instead for a cleaner look.
        JPanel sidePanel = new JPanel(new BorderLayout());
        JLabel header = new JLabel("Online Devices");
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBorder(new EmptyBorder(0, 0, 10, 0));
        header.setForeground(UIManager.getColor("Label.disabledForeground"));

        sidePanel.add(header, BorderLayout.NORTH);
        sidePanel.add(scrollPane, BorderLayout.CENTER);

        add(sidePanel, BorderLayout.WEST);
    }

    private void initRightPanel() {
        JPanel rightContainer = new JPanel(new BorderLayout(10, 10));

        // Top Panel: Status + Progress
        JPanel topPanel = new JPanel(new BorderLayout());
        connectionStatusLabel = new JLabel("Select a device");
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 18)); // Larger, modern font

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        // Make progress bar thinner
        progressBar.setPreferredSize(new Dimension(100, 4));

        topPanel.add(connectionStatusLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        // Center Panel: Chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 15)); // Readable font
        // Add padding inside the text area
        chatArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"))); // Subtle border

        // Bottom Panel: Input + File Button
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));

        messageField = new JTextField();
        messageField.putClientProperty("JTextField.placeholderText", "Type a message..."); // FlatLaf feature!
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        sendButton = new JButton("Send");
        sendFileButton = new JButton("File");

        // Make buttons slightly larger/friendlier
        sendButton.setFocusPainted(false);
        sendFileButton.setFocusPainted(false);

        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        sendFileButton.addActionListener(e -> selectAndSendFile());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        rightContainer.add(topPanel, BorderLayout.NORTH);
        rightContainer.add(chatScroll, BorderLayout.CENTER);
        rightContainer.add(inputPanel, BorderLayout.SOUTH);
        add(rightContainer, BorderLayout.CENTER);
    }

    private void sendMessage() {
        String msg = messageField.getText();
        if (selectedIp != null && !msg.trim().isEmpty()) {
            networkManager.sendMessageTo(selectedIp, msg);
            appendChat("Me", msg);
            messageField.setText("");
        }
    }

    private void selectAndSendFile() {
        if (selectedIp == null) {
            JOptionPane.showMessageDialog(this, "Select a device first.");
            return;
        }
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            networkManager.sendFileTo(selectedIp, f);
            appendChat("Me", "Sending file: " + f.getName());
        }
    }

    private void appendChat(String sender, String msg) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(sender + ": " + msg + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private void startDeviceDiscoveryTimer() {
        new Timer(3000, e -> {
            List<String> online = DeviceRegistry.getOnlineDevices();
            String currentSelection = deviceList.getSelectedValue();
            deviceListModel.clear();
            for (String ip : online) deviceListModel.addElement(ip);
            if (currentSelection != null && deviceListModel.contains(currentSelection)) {
                deviceList.setSelectedValue(currentSelection, true);
            }
        }).start();
    }

    @Override
    public void onMessage(String ip, String msg) {
        appendChat(ip, msg);
    }

    @Override
    public void onStatusChange(String ip, boolean online) {
        SwingUtilities.invokeLater(() -> chatArea.append("[System]: " + ip + (online ? " joined" : " left") + "\n"));
    }

    @Override
    public void onFileProgress(String ip, String fileName, int percent) {
        SwingUtilities.invokeLater(() -> {
            if (!progressBar.isVisible()) progressBar.setVisible(true);
            progressBar.setValue(percent);
            progressBar.setString(fileName + " " + percent + "%");

            setTitle("JConnect - Transferring: " + percent + "%");

            if (percent >= 100) {
                new Timer(2000, e -> {
                    progressBar.setVisible(false);
                    setTitle("JConnect P2P Messenger");
                    ((Timer)e.getSource()).stop();
                }).start();
            }
        });
    }

    public static void main(String[] args) {
        // 1. SETUP FLATLAF
        try {
            FlatDarkLaf.setup();

            // Optional: Customize global settings
            UIManager.put("Button.arc", 12);         // Rounded buttons
            UIManager.put("Component.arc", 12);      // Rounded fields
            UIManager.put("ProgressBar.arc", 12);    // Rounded progress bar
            UIManager.put("ScrollBar.width", 10);    // Thinner scrollbars
        } catch (Exception ex) {
            System.err.println("Failed to initialize FlatLaf");
        }

        SwingUtilities.invokeLater(App::new);
    }
}
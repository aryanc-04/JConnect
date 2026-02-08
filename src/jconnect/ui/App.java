package jconnect.ui;

import jconnect.network.*;
import javax.swing.*;
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
        setSize(900, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

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
        scrollPane.setPreferredSize(new Dimension(200, 0));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Online Devices"));
        add(scrollPane, BorderLayout.WEST);
    }

    private void initRightPanel() {
        JPanel rightContainer = new JPanel(new BorderLayout(5, 5));

        // Top Panel: Status + Progress
        JPanel topPanel = new JPanel(new BorderLayout());
        connectionStatusLabel = new JLabel("Select a device");
        connectionStatusLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        topPanel.add(connectionStatusLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);
        topPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // Center Panel: Chat
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);

        // Bottom Panel: Input + File Button
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        messageField = new JTextField();
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        sendButton = new JButton("Send");
        sendFileButton = new JButton("Attach File");

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
            
            // Flash window title as requested
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
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
        catch (Exception ignored) {}
        SwingUtilities.invokeLater(App::new);
    }
}
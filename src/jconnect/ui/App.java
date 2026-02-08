package jconnect.ui;

import jconnect.network.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class App extends JFrame implements ConnectionObserver {
    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    private JTextArea chatArea;
    private JTextField messageField;
    private NetworkManager networkManager;
    private String selectedDeviceIp = null;

    public App() {
        setTitle("JConnect P2P");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(10, 10));

        initComponents();
        
        // Initialize Network
        networkManager = new NetworkManager(this);
        networkManager.start();

        // Start a UI timer to refresh the discovery list every 2 seconds
        new Timer(2000, e -> refreshDeviceList()).start();

        setVisible(true);
    }

    private void initComponents() {
        // Left Panel: Device List
        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                selectedDeviceIp = deviceList.getSelectedValue();
                setTitle("JConnect - Chatting with " + selectedDeviceIp);
            }
        });
        add(new JScrollPane(deviceList), BorderLayout.WEST);

        // Right Panel: Chat
        JPanel chatPanel = new JPanel(new BorderLayout());
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatPanel.add(new JScrollPane(chatArea), BorderLayout.CENTER);

        // Input Field
        messageField = new JTextField();
        messageField.addActionListener(e -> sendMessage());
        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> sendMessage());

        JPanel inputPanel = new JPanel(new BorderLayout());
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        add(chatPanel, BorderLayout.CENTER);
    }

    private void refreshDeviceList() {
        List<String> online = DeviceRegistry.getOnlineDevices();
        // Update list model without clearing selection
        String current = selectedDeviceIp;
        deviceListModel.clear();
        for (String ip : online) deviceListModel.addElement(ip);
        if (current != null) deviceList.setSelectedValue(current, true);
    }

    private void sendMessage() {
        String msg = messageField.getText();
        if (selectedDeviceIp != null && !msg.isEmpty()) {
            networkManager.sendMessageTo(selectedDeviceIp, msg);
            chatArea.append("Me: " + msg + "\n");
            messageField.setText("");
        }
    }

    @Override
    public void onMessage(String ip, String message) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("[" + ip + "]: " + message + "\n");
        });
    }

    @Override
    public void onStatusChange(String ip, boolean isOnline) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append("System: " + ip + (isOnline ? " is online" : " went offline") + "\n");
        });
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
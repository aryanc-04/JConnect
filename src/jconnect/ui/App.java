package jconnect.ui;

import jconnect.network.*;
import javax.swing.*;
import java.awt.*;
import java.util.List;

public class App extends JFrame implements ConnectionObserver {
    private DefaultListModel<String> deviceListModel = new DefaultListModel<>();
    private JList<String> deviceList = new JList<>(deviceListModel);
    private JTextArea chatArea = new JTextArea();
    private JTextField messageField = new JTextField();
    private NetworkManager networkManager;
    private String selectedIp;

    public App() {
        setTitle("JConnect P2P Messenger");
        setSize(800, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout(5, 5));

        // UI Setup
        deviceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) selectedIp = deviceList.getSelectedValue();
        });
        add(new JScrollPane(deviceList), BorderLayout.WEST);

        chatArea.setEditable(false);
        add(new JScrollPane(chatArea), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(messageField, BorderLayout.CENTER);
        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> send());
        messageField.addActionListener(e -> send());
        bottom.add(sendBtn, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        // Logic Initialization
        networkManager = new NetworkManager(this);
        networkManager.start();

        // Refresh Sidebar every 3 seconds
        new Timer(3000, e -> {
            List<String> online = DeviceRegistry.getOnlineDevices();
            deviceListModel.clear();
            for (String ip : online) deviceListModel.addElement(ip);
        }).start();

        setVisible(true);
    }

    private void send() {
        String msg = messageField.getText();
        if (selectedIp != null && !msg.isEmpty()) {
            networkManager.sendMessageTo(selectedIp, msg);
            chatArea.append("Me: " + msg + "\n");
            messageField.setText("");
        }
    }

    @Override
    public void onMessage(String ip, String msg) {
        SwingUtilities.invokeLater(() -> chatArea.append("[" + ip + "]: " + msg + "\n"));
    }

    @Override
    public void onStatusChange(String ip, boolean online) {
        SwingUtilities.invokeLater(() -> chatArea.append("SYSTEM: " + ip + (online ? " joined." : " left.") + "\n"));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(App::new);
    }
}
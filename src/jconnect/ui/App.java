package jconnect.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import jconnect.network.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.List;

public class App extends JFrame implements ConnectionObserver {

    private NetworkManager networkManager;
    private String selectedIp;

    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    private JPanel chatPanel;
    private JScrollPane chatScrollPane;
    private JTextField messageField;
    private JLabel connectionStatusLabel;
    private JButton sendButton, sendFileButton;
    private JProgressBar progressBar;

    public App() {
        setTitle("JConnect P2P Messenger");
        setSize(950, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel mainContainer = new JPanel(new BorderLayout(15, 15));
        mainContainer.setBorder(new EmptyBorder(15, 15, 15, 15));
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
        deviceList.setFixedCellHeight(35);
        deviceList.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        deviceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String ip = deviceList.getSelectedValue();
                if (ip != null) {
                    selectedIp = ip;
                    connectionStatusLabel.setText("Chatting with: " + selectedIp);
                    chatPanel.removeAll();
                    chatPanel.revalidate();
                    chatPanel.repaint();
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(deviceList);
        scrollPane.setPreferredSize(new Dimension(220, 0));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

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

        JPanel topPanel = new JPanel(new BorderLayout());
        connectionStatusLabel = new JLabel("Select a device");
        connectionStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));

        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(100, 4));

        topPanel.add(connectionStatusLabel, BorderLayout.CENTER);
        topPanel.add(progressBar, BorderLayout.SOUTH);

        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(UIManager.getColor("List.background"));

        JPanel verticalWrapper = new JPanel(new BorderLayout());
        verticalWrapper.add(chatPanel, BorderLayout.NORTH);
        verticalWrapper.setBackground(UIManager.getColor("List.background"));

        chatScrollPane = new JScrollPane(verticalWrapper);
        chatScrollPane.setBorder(BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor")));
        chatScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        messageField = new JTextField();
        messageField.putClientProperty("JTextField.placeholderText", "Type a message...");
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 8, 0));
        sendButton = new JButton("Send");
        sendFileButton = new JButton("File");

        buttonPanel.add(sendButton);
        buttonPanel.add(sendFileButton);

        sendButton.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        sendFileButton.addActionListener(e -> selectAndSendFile());

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);

        rightContainer.add(topPanel, BorderLayout.NORTH);
        rightContainer.add(chatScrollPane, BorderLayout.CENTER);
        rightContainer.add(inputPanel, BorderLayout.SOUTH);
        add(rightContainer, BorderLayout.CENTER);
    }

    private void sendMessage() {
        String msg = messageField.getText();
        if (selectedIp != null && !msg.trim().isEmpty()) {
            networkManager.sendMessageTo(selectedIp, msg);
            addBubble(msg, true);
            messageField.setText("");
        }
    }

    private void addBubble(String text, boolean isMe) {
        SwingUtilities.invokeLater(() -> {
            BubblePane bubble = new BubblePane(text, isMe);
            chatPanel.add(bubble);
            chatPanel.add(Box.createVerticalStrut(10));

            chatPanel.revalidate();
            chatPanel.repaint();
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    @Override
    public void onMessage(String ip, String msg) {
        addBubble(msg, false);
    }

    @Override
    public void onStatusChange(String ip, boolean online) {
        SwingUtilities.invokeLater(() -> {
            if(ip.equals(selectedIp) && !online) {
                connectionStatusLabel.setText(ip + " is Offline");
            }
        });
    }

    @Override
    public void onFileProgress(String ip, String fileName, int percent) {
        SwingUtilities.invokeLater(() -> {
            if (!progressBar.isVisible()) progressBar.setVisible(true);
            progressBar.setValue(percent);
            progressBar.setString("Transferred: " + percent + "%");
            if (percent >= 100) {
                new Timer(2000, e -> {
                    progressBar.setVisible(false);
                    ((Timer)e.getSource()).stop();
                }).start();
                addBubble((ip.equals(selectedIp) ? "Received File: " : "Sent File: ") + fileName, false);
            }
        });
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
            addBubble("Sending file: " + f.getName(), true);
        }
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

    public static void main(String[] args) {
        try {
            FlatDarkLaf.setup();
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("ProgressBar.arc", 12);
        } catch (Exception ex) {}
        SwingUtilities.invokeLater(App::new);
    }

    private static class BubblePane extends JPanel {
        public BubblePane(String text, boolean isMe) {
            setLayout(new BorderLayout());
            setOpaque(false);

            JTextArea textArea = new JTextArea(text);
            textArea.setWrapStyleWord(true);
            textArea.setLineWrap(true);
            textArea.setOpaque(false);
            textArea.setEditable(false);
            textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));

            textArea.setForeground(isMe ? Color.WHITE : UIManager.getColor("Label.foreground"));

            int maxWidth = 400;
            textArea.setSize(maxWidth, Short.MAX_VALUE);
            Dimension preferredSize = textArea.getPreferredSize();
            textArea.setPreferredSize(preferredSize);

            JPanel bubble = new JPanel(new BorderLayout());
            bubble.setOpaque(false);
            bubble.add(textArea, BorderLayout.CENTER);
            bubble.setBorder(new EmptyBorder(10, 10, 10, 10));

            JPanel paintedBubble = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                    if (isMe) {
                        g2.setColor(new Color(66, 133, 244));
                    } else {
                        g2.setColor(UIManager.getColor("Button.background"));
                    }

                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            paintedBubble.setOpaque(false);
            paintedBubble.add(bubble);

            JPanel wrapper = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT));
            wrapper.setOpaque(false);
            wrapper.add(paintedBubble);

            add(wrapper, BorderLayout.CENTER);
        }
    }
}
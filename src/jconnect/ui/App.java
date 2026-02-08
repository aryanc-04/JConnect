package jconnect.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import jconnect.network.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.border.MatteBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.*;
import java.util.List;

public class App extends JFrame implements ConnectionObserver {

    private NetworkManager networkManager;
    private String currentChatIp; 
    private final Map<String, List<ChatMessage>> chatHistory = new HashMap<>();
    private boolean isUpdatingList = false;

    private DefaultListModel<String> deviceListModel;
    private JList<String> deviceList;
    
    private JScrollPane chatScrollPane;
    private JPanel chatCardPanel; 
    private JPanel rightPanel;    
    private JLabel headerTitle;
    private JLabel headerStatus;
    private JTextField messageField;
    private JProgressBar fileProgressBar;
    private JLabel dotLabel;

    private final Color COL_BG_DARK = new Color(33, 37, 43);      
    private final Color COL_BG_LIGHT = new Color(40, 44, 52);     
    private final Color COL_ACCENT = new Color(61, 119, 215);     
    private final Font FONT_MAIN = new Font("Segoe UI", Font.PLAIN, 14);
    private final Font FONT_BOLD = new Font("Segoe UI", Font.BOLD, 15);

    public App() {
        networkManager = new NetworkManager(this);
        networkManager.start();

        setTitle("JConnect");
        setSize(1080, 760);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setBackground(COL_BG_DARK);

        JPanel mainLayout = new JPanel(new BorderLayout());
        setContentPane(mainLayout);

        JPanel sidebar = createSidebar();
        createRightPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, rightPanel);
        splitPane.setDividerLocation(280); 
        splitPane.setDividerSize(1);       
        splitPane.setBorder(null);
        mainLayout.add(splitPane, BorderLayout.CENTER);

        startDeviceDiscovery();

        setVisible(true);
    }

    private JPanel createSidebar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(COL_BG_DARK);
        panel.setBorder(new EmptyBorder(0, 0, 0, 1)); 

        JLabel title = new JLabel("Devices Near You");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(new Color(150, 150, 150));
        title.setBorder(new EmptyBorder(20, 20, 10, 20));
        panel.add(title, BorderLayout.NORTH);

        deviceListModel = new DefaultListModel<>();
        deviceList = new JList<>(deviceListModel);
        deviceList.setBackground(COL_BG_DARK);
        deviceList.setForeground(Color.WHITE);
        deviceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        deviceList.setCellRenderer(new DeviceListRenderer());
        
        deviceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !isUpdatingList) {
                String selected = deviceList.getSelectedValue();
                if (selected != null) {
                    switchChat(selected);
                }
            }
        });

        JScrollPane scroll = new JScrollPane(deviceList);
        scroll.setBorder(null);
        styleScrollBar(scroll.getVerticalScrollBar());
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void createRightPanel() {
        rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(COL_BG_LIGHT);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(COL_BG_DARK);
        header.setPreferredSize(new Dimension(0, 65));
        header.setBorder(new MatteBorder(0, 0, 1, 0, new Color(45, 49, 58)));

        JPanel headerInfo = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 12));
        headerInfo.setOpaque(false);

        dotLabel = new JLabel("●");
        dotLabel.setFont(new Font("SansSerif", Font.PLAIN, 24));
        dotLabel.setForeground(Color.GRAY);

        headerTitle = new JLabel("Select a Device");
        headerTitle.setFont(FONT_BOLD);
        headerTitle.setForeground(Color.WHITE);

        headerStatus = new JLabel("Offline");
        headerStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        headerStatus.setForeground(Color.GRAY);

        JPanel textStack = new JPanel(new GridLayout(2, 1));
        textStack.setOpaque(false);
        textStack.add(headerTitle);
        textStack.add(headerStatus);

        headerInfo.add(dotLabel);
        headerInfo.add(textStack);

        header.add(headerInfo, BorderLayout.WEST);

        chatCardPanel = new JPanel();
        chatCardPanel.setLayout(new BoxLayout(chatCardPanel, BoxLayout.Y_AXIS));
        chatCardPanel.setBackground(COL_BG_LIGHT);
        chatCardPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(COL_BG_LIGHT);
        wrapper.add(chatCardPanel, BorderLayout.NORTH);

        chatScrollPane = new JScrollPane(wrapper);
        chatScrollPane.setBorder(null);
        chatScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        styleScrollBar(chatScrollPane.getVerticalScrollBar());

        JPanel footer = new JPanel(new BorderLayout(15, 0));
        footer.setBackground(COL_BG_DARK);
        footer.setBorder(new EmptyBorder(15, 20, 15, 20));

        messageField = new JTextField();
        messageField.setFont(FONT_MAIN);
        messageField.setBackground(new Color(45, 49, 55));
        messageField.setForeground(Color.WHITE);
        messageField.setCaretColor(Color.WHITE);
        messageField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(new Color(60, 60, 60), 1, true),
                new EmptyBorder(5, 10, 5, 10)
        ));
        
        JButton attachBtn = new JButton("+");
        styleButton(attachBtn, new Color(50, 54, 62));
        attachBtn.setPreferredSize(new Dimension(40, 40));
        
        JButton sendBtn = new JButton("Send");
        styleButton(sendBtn, COL_ACCENT);
        sendBtn.setPreferredSize(new Dimension(80, 40));

        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setPreferredSize(new Dimension(0, 4));
        fileProgressBar.setForeground(COL_ACCENT);
        fileProgressBar.setBorder(null);
        fileProgressBar.setVisible(false);

        JPanel southStack = new JPanel(new BorderLayout());
        southStack.add(fileProgressBar, BorderLayout.NORTH);
        southStack.add(footer, BorderLayout.CENTER);

        footer.add(attachBtn, BorderLayout.WEST);
        footer.add(messageField, BorderLayout.CENTER);
        footer.add(sendBtn, BorderLayout.EAST);

        sendBtn.addActionListener(e -> sendMessage());
        messageField.addActionListener(e -> sendMessage());
        attachBtn.addActionListener(e -> sendFile());

        rightPanel.add(header, BorderLayout.NORTH);
        rightPanel.add(chatScrollPane, BorderLayout.CENTER);
        rightPanel.add(southStack, BorderLayout.SOUTH);
    }

    private void switchChat(String ip) {
        if (ip == null) return;
        currentChatIp = ip;
        
        chatCardPanel.removeAll();
        
        chatHistory.putIfAbsent(ip, new ArrayList<>());
        for (ChatMessage msg : chatHistory.get(ip)) {
            addBubbleToUI(msg);
        }
        
        headerTitle.setText("Chat with " + ip);
        networkManager.connectTo(ip);
        
        refreshUI();
    }

    private void sendMessage() {
        String text = messageField.getText().trim();
        if (currentChatIp != null && !text.isEmpty()) {
            messageField.setText(""); 
            
            networkManager.sendMessageTo(currentChatIp, text);
            addMessage(currentChatIp, text, true, false);
        }
    }

    private void sendFile() {
        if (currentChatIp == null) return;
        JFileChooser fc = new JFileChooser();
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            networkManager.sendFileTo(currentChatIp, f);
            addMessage(currentChatIp, "Sending: " + f.getName(), true, true);
        }
    }

    private void addMessage(String ip, String text, boolean isMe, boolean isFile) {
        ChatMessage msg = new ChatMessage(text, isMe, isFile);
        chatHistory.putIfAbsent(ip, new ArrayList<>());
        chatHistory.get(ip).add(msg);

        if (ip.equals(currentChatIp)) {
            addBubbleToUI(msg);
            refreshUI();
        }
    }

    private void addBubbleToUI(ChatMessage msg) {
        JPanel bubbleRow = new JPanel(new FlowLayout(msg.isMe() ? FlowLayout.RIGHT : FlowLayout.LEFT));
        bubbleRow.setOpaque(false);
        bubbleRow.setBorder(new EmptyBorder(2, 0, 2, 0));

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(msg.isMe() ? COL_ACCENT : new Color(55, 60, 68));
        bubble.setBorder(new EmptyBorder(10, 15, 10, 15));
        
        JLabel content = new JLabel("<html><body style='width: 300px;'>" + msg.getContent() + "</body></html>");
        content.setForeground(Color.WHITE);
        content.setFont(FONT_MAIN);
        
        if (msg.isFile()) {
            content.setIcon(UIManager.getIcon("FileView.fileIcon"));
            content.setText(" " + msg.getContent());
        }

        JLabel time = new JLabel(msg.getTimestamp());
        time.setFont(new Font("SansSerif", Font.PLAIN, 10));
        time.setForeground(new Color(200, 200, 200));
        time.setHorizontalAlignment(SwingConstants.RIGHT);

        bubble.add(content, BorderLayout.CENTER);
        bubble.add(time, BorderLayout.SOUTH);

        bubbleRow.add(bubble);
        chatCardPanel.add(bubbleRow);
    }

    private void updateConnectionStatus(boolean online) {
        headerStatus.setText(online ? "Connected" : "Offline");
        headerStatus.setForeground(online ? Color.GREEN : Color.GRAY);
        dotLabel.setForeground(online ? Color.GREEN : Color.GRAY);
    }

    @Override
    public void onMessage(String ip, String msg) {
        SwingUtilities.invokeLater(() -> addMessage(ip, msg, false, false));
    }

    @Override
    public void onStatusChange(String ip, boolean online) {
        SwingUtilities.invokeLater(() -> {
            if (ip.equals(currentChatIp)) {
                updateConnectionStatus(online);
            }
        });
    }

    @Override
    public void onFileProgress(String ip, String file, int percent) {
        SwingUtilities.invokeLater(() -> {
            if (ip.equals(currentChatIp)) {
                fileProgressBar.setVisible(true);
                fileProgressBar.setValue(percent);
                if (percent >= 100) {
                     new javax.swing.Timer(1500, e -> {
                         fileProgressBar.setVisible(false);
                         ((javax.swing.Timer)e.getSource()).stop();
                     }).start();
                }
            }
        });
    }

    private void refreshUI() {
        chatCardPanel.revalidate();
        chatCardPanel.repaint();
        
        SwingUtilities.invokeLater(() -> {
            if (chatScrollPane != null && chatScrollPane.getVerticalScrollBar() != null) {
                chatScrollPane.getVerticalScrollBar().setValue(chatScrollPane.getVerticalScrollBar().getMaximum());
            }
        });
    }

    private void startDeviceDiscovery() {
        new javax.swing.Timer(2000, e -> {
            List<String> online = DeviceRegistry.getOnlineDevices();
            Collections.sort(online);
            
            boolean changed = false;
            if (online.size() != deviceListModel.size()) changed = true;
            else {
                for (int i=0; i<online.size(); i++) {
                    if (!online.get(i).equals(deviceListModel.get(i))) changed = true;
                }
            }
            
            if (changed) {
                isUpdatingList = true;
                String selected = deviceList.getSelectedValue();
                deviceListModel.clear();
                for(String s : online) deviceListModel.addElement(s);
                if (selected != null && deviceListModel.contains(selected)) {
                    deviceList.setSelectedValue(selected, false); 
                }
                isUpdatingList = false;
            }
        }).start();
    }

    private void styleButton(JButton btn, Color bg) {
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setBorder(new EmptyBorder(8, 15, 8, 15));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        for(java.awt.event.MouseListener ml : btn.getMouseListeners()) {
            if(ml.getClass().getName().contains("App")) btn.removeMouseListener(ml);
        }

        btn.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { btn.setBackground(bg.brighter()); }
            public void mouseExited(MouseEvent e) { btn.setBackground(bg); }
        });
    }

    private void styleScrollBar(JScrollBar sb) {
        sb.setBackground(COL_BG_LIGHT);
        sb.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = new Color(60, 64, 72);
                this.trackColor = COL_BG_LIGHT;
            }
            @Override
            protected JButton createDecreaseButton(int orientation) { return createZeroButton(); }
            @Override
            protected JButton createIncreaseButton(int orientation) { return createZeroButton(); }
            private JButton createZeroButton() {
                JButton jbutton = new JButton();
                jbutton.setPreferredSize(new Dimension(0, 0));
                jbutton.setMinimumSize(new Dimension(0, 0));
                jbutton.setMaximumSize(new Dimension(0, 0));
                return jbutton;
            }
        });
    }

    private class DeviceListRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(10, 0));
            p.setBorder(new EmptyBorder(12, 15, 12, 15));
            p.setBackground(isSelected ? new Color(45, 49, 58) : COL_BG_DARK);

            JLabel icon = new JLabel("🖥️"); 
            icon.setForeground(Color.LIGHT_GRAY);
            
            JLabel text = new JLabel(value.toString());
            text.setFont(FONT_MAIN);
            text.setForeground(isSelected ? Color.WHITE : Color.LIGHT_GRAY);

            p.add(icon, BorderLayout.WEST);
            p.add(text, BorderLayout.CENTER);
            return p;
        }
    }

    public static void main(String[] args) {
        try { FlatDarkLaf.setup(); } catch(Exception e){}
        SwingUtilities.invokeLater(App::new);
    }
}
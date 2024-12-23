import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.text.*;
import java.text.SimpleDateFormat;

public class ChatClientGUI extends JFrame {
    private static final String CONFIG_FILE = "chat-config.txt";
    private static String SERVER_ADDRESS;
    private static int SERVER_PORT;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private JTextPane chatArea;
    private JTextField messageField;
    private JTextField nameField;
    private String username;
    private javax.swing.Timer refreshTimer;
    private JPanel cards;
    private CardLayout cardLayout;
    private StyledDocument doc;
    private Style baseStyle;
    private Style systemStyle;
    private Style userStyle;

    public ChatClientGUI() {
        super("Chat Application");
        loadConfig();
        initializeStyles();
        setupGUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null);
    }

    private void initializeStyles() {
        doc = new DefaultStyledDocument();
        baseStyle = doc.addStyle("base", null);
        StyleConstants.setFontFamily(baseStyle, "Arial");
        StyleConstants.setFontSize(baseStyle, 14);

        systemStyle = doc.addStyle("system", baseStyle);
        StyleConstants.setForeground(systemStyle, new Color(100, 100, 100));
        StyleConstants.setItalic(systemStyle, true);

        userStyle = doc.addStyle("user", baseStyle);
        StyleConstants.setForeground(userStyle, new Color(0, 102, 204));
        StyleConstants.setBold(userStyle, true);
    }

    private void setupGUI() {
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);

        // Login Panel
        JPanel loginPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);

        JLabel nameLabel = new JLabel("Enter your name:");
        nameField = new JTextField(20);
        JButton connectButton = new JButton("Connect");
        
        nameField.addActionListener(e -> connectToServer());
        connectButton.addActionListener(e -> connectToServer());

        loginPanel.add(nameLabel, gbc);
        gbc.gridy = 1;
        loginPanel.add(nameField, gbc);
        gbc.gridy = 2;
        loginPanel.add(connectButton, gbc);

        // Chat Panel
        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        chatArea = new JTextPane(doc);
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        messageField = new JTextField();
        JButton sendButton = new JButton("Send");

        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(sendButton, BorderLayout.EAST);

        // Toolbar
        JToolBar toolbar = new JToolBar();
        toolbar.setFloatable(false);
        JButton modifyButton = new JButton("Modify");
        JButton deleteButton = new JButton("Delete");
        JButton historyButton = new JButton("History");

        toolbar.add(modifyButton);
        toolbar.add(deleteButton);
        toolbar.add(historyButton);

        chatPanel.add(toolbar, BorderLayout.NORTH);
        chatPanel.add(scrollPane, BorderLayout.CENTER);
        chatPanel.add(inputPanel, BorderLayout.SOUTH);

        // Action Listeners
        messageField.addActionListener(e -> sendMessage());
        sendButton.addActionListener(e -> sendMessage());

        modifyButton.addActionListener(e -> {
            String messageId = JOptionPane.showInputDialog("Enter message ID to modify:");
            if (messageId != null && !messageId.trim().isEmpty()) {
                String newContent = JOptionPane.showInputDialog("Enter new message:");
                if (newContent != null && !newContent.trim().isEmpty()) {
                    out.println("MODIFY:" + messageId + ":" + newContent);
                }
            }
        });

        deleteButton.addActionListener(e -> {
            String messageId = JOptionPane.showInputDialog("Enter message ID to delete:");
            if (messageId != null && !messageId.trim().isEmpty()) {
                out.println("DELETE:" + messageId);
            }
        });

        historyButton.addActionListener(e -> {
            String date = JOptionPane.showInputDialog("Enter date (yyyy-MM-dd):");
            if (date != null && !date.trim().isEmpty()) {
                out.println("HISTORY:" + date);
            }
        });

        cards.add(loginPanel, "login");
        cards.add(chatPanel, "chat");
        add(cards);

        refreshTimer = new javax.swing.Timer(5000, e -> refreshChat());
    }

    private void connectToServer() {
        username = nameField.getText().trim();
        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a name", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Start message receiving thread
            new Thread(this::receiveMessages).start();

            cardLayout.show(cards, "chat");
            messageField.requestFocus();
            refreshTimer.start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error connecting to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            out.println(message);
            messageField.setText("");
        }
    }

    private void refreshChat() {
        chatArea.repaint();
    }

    private void receiveMessages() {
        try {
            String message;
            while ((message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    try {
                        if (finalMessage.contains("a rejoint le chat") || finalMessage.contains("a quitté le chat")) {
                            doc.insertString(doc.getLength(), finalMessage + "\n", systemStyle);
                        } else {
                            doc.insertString(doc.getLength(), finalMessage + "\n", userStyle);
                        }
                        chatArea.setCaretPosition(doc.getLength());
                    } catch (BadLocationException e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(ChatClientGUI.this,
                        "Connection to server lost: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
            });
        }
    }

    private void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            Properties prop = new Properties();
            prop.load(input);
            SERVER_ADDRESS = prop.getProperty("SERVER_ADDRESS");
            SERVER_PORT = Integer.parseInt(prop.getProperty("SERVER_PORT"));
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Error loading configuration file",
                    "Configuration Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        SwingUtilities.invokeLater(() -> {
            new ChatClientGUI().setVisible(true);
        });
    }
}
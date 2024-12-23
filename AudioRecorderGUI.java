import javax.swing.*;
import javax.swing.border.*;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class AudioRecorderGUI extends JFrame {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private Properties config;
    private boolean isRecording = false;
    private JButton recordButton;
    private JList<String> recordingsList;
    private DefaultListModel<String> listModel;
    private JButton playButton;
    private JButton deleteButton;
    private JLabel statusLabel;
    private javax.swing.Timer refreshTimer;

    public AudioRecorderGUI() {
        loadConfig();
        setupModernUI();
        initializeGUI();
        connectToServer();
        showLoginDialog();
        startAutoRefresh();
    }

    private void setupModernUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Button.arc", 15);
            UIManager.put("Component.focusWidth", 1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeGUI() {
        setTitle("Modern Audio Recorder");
        setSize(800, 600);
        setMinimumSize(new Dimension(600, 400));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout(15, 15));
        getContentPane().setBackground(new Color(245, 245, 245));

        JPanel headerPanel = createHeaderPanel();
        add(headerPanel, BorderLayout.NORTH);

        JPanel mainContent = createMainContentPanel();
        add(mainContent, BorderLayout.CENTER);

        JPanel controlPanel = createControlPanel();
        add(controlPanel, BorderLayout.SOUTH);

        setLocationRelativeTo(null);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(41, 128, 185));
        header.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        statusLabel = new JLabel("Not connected", SwingConstants.LEFT);
        statusLabel.setForeground(Color.WHITE);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        header.add(statusLabel, BorderLayout.WEST);

        return header;
    }

    private JPanel createMainContentPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBackground(new Color(245, 245, 245));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        listModel = new DefaultListModel<>();
        recordingsList = new JList<>(listModel);
        recordingsList.setBackground(Color.WHITE);
        recordingsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        recordingsList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        recordingsList.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(recordingsList);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        return mainPanel;
    }

    private JPanel createControlPanel() {
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        controlPanel.setBackground(new Color(245, 245, 245));
        controlPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        recordButton = createStyledButton("Record", new Color(231, 76, 60));
        playButton = createStyledButton("Play", new Color(46, 204, 113));
        deleteButton = createStyledButton("Delete", new Color(142, 68, 173));

        recordButton.addActionListener(e -> handleRecordButton());
        playButton.addActionListener(e -> playRecording());
        deleteButton.addActionListener(e -> deleteRecording());

        controlPanel.add(recordButton);
        controlPanel.add(playButton);
        controlPanel.add(deleteButton);

        return controlPanel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(120, 40));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));

        button.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                button.setBackground(bgColor.darker());
            }
            public void mouseExited(MouseEvent e) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    private void startAutoRefresh() {
        refreshTimer = new javax.swing.Timer(5000, e -> refreshRecordingsList());
        refreshTimer.start();
    }

    private void loadConfig() {
        config = new Properties();
        try (InputStream input = new FileInputStream("audio-config.txt")) {
            config.load(input);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error loading configuration", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void connectToServer() {
        try {
            socket = new Socket(config.getProperty("SERVER_ADDRESS"), 
                              Integer.parseInt(config.getProperty("PORT")));
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            statusLabel.setText("Connected to server");
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Cannot connect to server", "Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void showLoginDialog() {
        JTextField usernameField = new JTextField();
        Object[] message = {
            "Username:", usernameField
        };

        int option = JOptionPane.showConfirmDialog(this, message, "Login", 
            JOptionPane.OK_CANCEL_OPTION);

        if (option == JOptionPane.OK_OPTION && !usernameField.getText().trim().isEmpty()) {
            username = usernameField.getText().trim();
            statusLabel.setText("Logged in as: " + username);
            registerUser();
        } else {
            System.exit(0);
        }
    }

    private void registerUser() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter("user.txt", true))) {
            String date = new Date().toString();
            writer.write(username + " - First Login: " + date + " Last Login: " + date + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleRecordButton() {
        if (!isRecording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            AudioFormat format = new AudioFormat(
                Float.parseFloat(config.getProperty("AUDIO_SAMPLE_RATE")),
                Integer.parseInt(config.getProperty("AUDIO_BITS")),
                Integer.parseInt(config.getProperty("AUDIO_CHANNELS")),
                Boolean.parseBoolean(config.getProperty("AUDIO_SIGNED")),
                Boolean.parseBoolean(config.getProperty("AUDIO_BIG_ENDIAN"))
            );

            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            isRecording = true;
            recordButton.setText("Stop");
            recordButton.setBackground(new Color(192, 57, 43));
            statusLabel.setText("Recording...");

            Thread recordingThread = new Thread(() -> {
                try {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while (isRecording) {
                        bytesRead = microphone.read(buffer, 0, buffer.length);
                        if (bytesRead > 0) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    microphone.close();
                    saveRecording(out.toByteArray(), format);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            recordingThread.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error accessing microphone", "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void stopRecording() {
        isRecording = false;
        recordButton.setText("Record");
        recordButton.setBackground(new Color(231, 76, 60));
        statusLabel.setText("Recording stopped");
    }

    private void saveRecording(byte[] audioData, AudioFormat format) {
        String fileName = "recording_" + username + "_" + System.currentTimeMillis() + ".wav";
        File file = new File("recordings", fileName);

        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }

            AudioInputStream ais = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                format,
                audioData.length / format.getFrameSize()
            );

            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, file);

            out.println("SEND_AUDIO");
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(audioData);
            outputStream.flush();

            try (BufferedWriter writer = new BufferedWriter(new FileWriter("audio-metadata.txt", true))) {
                writer.write(fileName + "," + username + "\n");
            }

            SwingUtilities.invokeLater(this::refreshRecordingsList);
            statusLabel.setText("Recording saved: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving recording", "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void playRecording() {
        String selectedRecording = recordingsList.getSelectedValue();
        if (selectedRecording == null) {
            JOptionPane.showMessageDialog(this, "Please select a recording to play", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            File file = new File("recordings", selectedRecording);
            AudioInputStream audioStream = AudioSystem.getAudioInputStream(file);
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);

            speakers.open(format);
            speakers.start();
            statusLabel.setText("Playing: " + selectedRecording);

            Thread playThread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[4096];
                    int bytesRead;

                    while ((bytesRead = audioStream.read(buffer)) != -1) {
                        speakers.write(buffer, 0, bytesRead);
                    }

                    speakers.drain();
                    speakers.close();
                    audioStream.close();
                    SwingUtilities.invokeLater(() -> 
                        statusLabel.setText("Playback finished: " + selectedRecording));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            playThread.start();
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error playing recording", "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteRecording() {
        String selectedRecording = recordingsList.getSelectedValue();
        if (selectedRecording == null) {
            JOptionPane.showMessageDialog(this, "Please select a recording to delete", 
                "No Selection", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            BufferedReader reader = new BufferedReader(new FileReader("audio-metadata.txt"));
            String line;
            boolean isOwner = false;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2 && parts[0].equals(selectedRecording) && 
                    parts[1].equals(username)) {
                    isOwner = true;
                    break;
                }
            }
            reader.close();

            if (!isOwner) {
                JOptionPane.showMessageDialog(this, "You can only delete your own recordings", 
                    "Permission Denied", JOptionPane.WARNING_MESSAGE);
                return;
            }

            File file = new File("recordings", selectedRecording);
            if (file.delete()) {
                refreshRecordingsList();
                statusLabel.setText("Deleted: " + selectedRecording);
            } else {
                JOptionPane.showMessageDialog(this, "Could not delete the recording", 
                    "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error deleting recording", "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void refreshRecordingsList() {
        listModel.clear();
        File recordingsDir = new File("recordings");
        if (recordingsDir.exists() && recordingsDir.isDirectory()) {
            File[] recordings = recordingsDir.listFiles((dir, name) -> name.endsWith(".wav"));
            if (recordings != null) {
                Arrays.sort(recordings, Comparator.comparing(File::lastModified).reversed());
                for (File recording : recordings) {
                    listModel.addElement(recording.getName());
                }
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new AudioRecorderGUI().setVisible(true);
        });
    }
}
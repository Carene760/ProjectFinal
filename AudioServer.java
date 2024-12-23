// ChatAudioServer.java
import java.io.*;
import java.net.*;
import java.util.*;

public class AudioServer {
    private static final String CONFIG_FILE = "audio-config.txt";
    private static String RECORDINGS_DIR;
    private static String METADATA_FILE;
    private static int MAX_AUDIO_SIZE_MB;
    private static List<String> ALLOWED_AUDIO_FORMATS;
    private static int PORT;

    public static void main(String[] args) {
        loadConfiguration();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT);
            File recordingsDir = new File(RECORDINGS_DIR);
            if (!recordingsDir.exists()) {
                recordingsDir.mkdir();
            }

            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfiguration() {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONFIG_FILE))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("RECORDINGS_DIR=")) {
                    RECORDINGS_DIR = line.split("=")[1].trim();
                } else if (line.startsWith("METADATA_FILE=")) {
                    METADATA_FILE = line.split("=")[1].trim();
                } else if (line.startsWith("MAX_AUDIO_SIZE_MB=")) {
                    MAX_AUDIO_SIZE_MB = Integer.parseInt(line.split("=")[1].trim());
                } else if (line.startsWith("ALLOWED_AUDIO_FORMATS=")) {
                    String formats = line.split("=")[1].trim();
                    ALLOWED_AUDIO_FORMATS = Arrays.asList(formats.split(","));
                } else if (line.startsWith("PORT=")) {
                    PORT = Integer.parseInt(line.split("=")[1].trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            RECORDINGS_DIR = "recordings";
            METADATA_FILE = "audio-metadata.txt";
            MAX_AUDIO_SIZE_MB = 50;
            ALLOWED_AUDIO_FORMATS = Arrays.asList("wav", "mp3");
            PORT = 12345;
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String username;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                while (true) {
                    String message = in.readLine();
                    if (message == null) {
                        break;
                    }

                    if (message.equals("SEND_AUDIO")) {
                        receiveAudio();
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void receiveAudio() {
            try {
                InputStream inputStream = socket.getInputStream();
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
                byte[] audioData = byteArrayOutputStream.toByteArray();
                
                if (audioData.length > MAX_AUDIO_SIZE_MB * 1024 * 1024) {
                    out.println("ERROR: File size exceeds " + MAX_AUDIO_SIZE_MB + " MB limit.");
                    return;
                }

                String fileType = "wav";
                if (!ALLOWED_AUDIO_FORMATS.contains(fileType)) {
                    out.println("ERROR: Invalid format. Allowed: " + ALLOWED_AUDIO_FORMATS);
                    return;
                }

                String fileName = "recording_" + System.currentTimeMillis() + ".wav";
                File file = new File(RECORDINGS_DIR, fileName);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(audioData);
                }

                try (BufferedWriter metadataWriter = new BufferedWriter(new FileWriter(METADATA_FILE, true))) {
                    metadataWriter.write(fileName + "," + username + "\n");
                }

                System.out.println("Audio saved: " + fileName);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
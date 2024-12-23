// ChatAudioClient.java
import java.io.*;
import java.net.*;
import java.util.*;
import javax.sound.sampled.*;

public class AudioClient {
    private static final String USER_FILE = "user.txt";
    private static final String RECORDINGS_DIR = "recordings";
    private static final String CONFIG_FILE = "audio-config.txt";
    private static final String METADATA_FILE = "audio-metadata.txt";
    private static String username;
    private static Socket socket;
    private static PrintWriter out;
    private static BufferedReader in;
    private static String serverAddress;
    private static int port;
    private static int bufferSize;
    private static float sampleRate;
    private static int bits;
    private static int channels;
    private static boolean signed;
    private static boolean bigEndian;

    public static void main(String[] args) {
        loadConfig();
        Scanner scanner = new Scanner(System.in);

        try {
            socket = new Socket(serverAddress, port);
            System.out.println("Connected to server at " + serverAddress + ":" + port);

            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            System.out.println("Please enter your username:");
            username = scanner.nextLine();
            registerUser(username);

            while (true) {
                System.out.println("\nSelect an option:");
                System.out.println("1. Record audio");
                System.out.println("2. Display all recordings");
                System.out.println("3. Listen to a recording");
                System.out.println("4. Delete a recording");
                System.out.println("5. Quit");

                String option = scanner.nextLine();
                switch (option) {
                    case "1":
                        recordAudio(scanner);
                        break;
                    case "2":
                        displayRecordings();
                        break;
                    case "3":
                        listenToRecording(scanner);
                        break;
                    case "4":
                        deleteRecording(scanner);
                        break;
                    case "5":
                        System.out.println("Exiting...");
                        socket.close();
                        return;
                    default:
                        System.out.println("Invalid option, please try again.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void loadConfig() {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            properties.load(input);
            serverAddress = properties.getProperty("SERVER_ADDRESS");
            port = Integer.parseInt(properties.getProperty("PORT"));
            bufferSize = Integer.parseInt(properties.getProperty("BUFFER_SIZE"));
            sampleRate = Float.parseFloat(properties.getProperty("AUDIO_SAMPLE_RATE"));
            bits = Integer.parseInt(properties.getProperty("AUDIO_BITS"));
            channels = Integer.parseInt(properties.getProperty("AUDIO_CHANNELS"));
            signed = Boolean.parseBoolean(properties.getProperty("AUDIO_SIGNED"));
            bigEndian = Boolean.parseBoolean(properties.getProperty("AUDIO_BIG_ENDIAN"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void registerUser(String username) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(USER_FILE, true))) {
            File file = new File(USER_FILE);
            if (!file.exists()) {
                file.createNewFile();
            }
            String date = new Date().toString();
            writer.write(username + " - First Login: " + date + " Last Login: " + date + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void recordAudio(Scanner scanner) {
        try {
            AudioFormat format = new AudioFormat(sampleRate, bits, channels, signed, bigEndian);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            System.out.println("Recording audio. Type 'STOP' to finish recording.");
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[bufferSize];
            int bytesRead;

            final boolean[] isRecording = {true};
            Thread inputThread = new Thread(() -> {
                while (isRecording[0]) {
                    String command = scanner.nextLine();
                    if (command.equalsIgnoreCase("STOP")) {
                        isRecording[0] = false;
                    }
                }
            });
            inputThread.start();

            while (isRecording[0]) {
                bytesRead = microphone.read(buffer, 0, buffer.length);
                if (bytesRead > 0) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }
            }

            microphone.stop();
            microphone.close();

            byte[] audioData = byteArrayOutputStream.toByteArray();
            String fileName = "recording_" + username + "_" + System.currentTimeMillis() + ".wav";
            File file = new File(RECORDINGS_DIR, fileName);
            saveAsWav(file, audioData, format);

            try (BufferedWriter metadataWriter = new BufferedWriter(new FileWriter(METADATA_FILE, true))) {
                metadataWriter.write(fileName + "," + username + "\n");
            }

            out.println("SEND_AUDIO");
            OutputStream outputStream = socket.getOutputStream();
            outputStream.write(audioData);
            outputStream.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveAsWav(File file, byte[] audioData, AudioFormat format) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            AudioInputStream audioStream = new AudioInputStream(
                new ByteArrayInputStream(audioData),
                format,
                audioData.length / format.getFrameSize()
            );
            AudioSystem.write(audioStream, AudioFileFormat.Type.WAVE, file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void displayRecordings() {
        File recordingsDir = new File(RECORDINGS_DIR);
        if (!recordingsDir.exists() || recordingsDir.listFiles() == null) {
            System.out.println("No recordings found.");
            return;
        }

        System.out.println("Available recordings:");
        for (File file : recordingsDir.listFiles()) {
            System.out.println(file.getName());
        }
    }

    private static void listenToRecording(Scanner scanner) {
        System.out.println("Enter the recording name:");
        String recordingName = scanner.nextLine();
        File file = new File(RECORDINGS_DIR, recordingName);

        if (!file.exists()) {
            System.out.println("Recording not found.");
            return;
        }

        try (AudioInputStream audioStream = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = audioStream.getFormat();
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine speakers = (SourceDataLine) AudioSystem.getLine(info);
            speakers.open(format);
            speakers.start();

            byte[] buffer = new byte[4096];
            int bytesRead;

            System.out.println("Playing recording. Type 'STOP' to stop playback.");
            while ((bytesRead = audioStream.read(buffer)) != -1) {
                speakers.write(buffer, 0, bytesRead);
                if (System.in.available() > 0) {
                    String command = scanner.nextLine();
                    if (command.equalsIgnoreCase("STOP")) {
                        break;
                    }
                }
            }

            speakers.drain();
            speakers.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void deleteRecording(Scanner scanner) {
        System.out.println("Enter the recording name:");
        String recordingName = scanner.nextLine();
        File file = new File(RECORDINGS_DIR, recordingName);

        if (!file.exists()) {
            System.out.println("Recording not found.");
            return;
        }

        try (BufferedReader metadataReader = new BufferedReader(new FileReader(METADATA_FILE))) {
            String line;
            boolean isOwner = false;
            while ((line = metadataReader.readLine()) != null) {
                String[] metadata = line.split(",");
                if (metadata.length == 2 && metadata[0].equals(recordingName) && metadata[1].equals(username)) {
                    isOwner = true;
                    break;
                }
            }

            if (!isOwner) {
                System.out.println("You can only delete your own recordings.");
                return;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        if (file.delete()) {
            System.out.println("Recording deleted successfully.");
        } else {
            System.out.println("Failed to delete recording.");
        }
    }
}
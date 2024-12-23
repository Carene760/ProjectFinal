import java.io.*;
import java.net.Socket;
import java.util.Scanner;
import java.util.Properties;

public class Client {
    private static String DOWNLOAD_DIRECTORY;
    private static Properties config = new Properties();
    private static int CHUNK_SIZE;
    private static ResumeManager resumeManager;
    private static String serverIP;
    private static int PORT;
    
    public static void loadConfig() {
        try {
            FileInputStream input = new FileInputStream("file-config.txt");
            config.load(input);
            DOWNLOAD_DIRECTORY = System.getProperty("user.home") + File.separator + "Downloads";
            CHUNK_SIZE = Integer.parseInt(config.getProperty("BLOCK_SIZE"));
            serverIP = config.getProperty("SERVER_ADDRESS");
            PORT = Integer.parseInt(config.getProperty("SERVER_PORT"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            loadConfig();
            
            resumeManager = new ResumeManager(config.getProperty("RESUME_TOKEN_FILE"));

            try (Scanner scanner = new Scanner(System.in)) {
                System.out.println("Enter your username:");
                String username = scanner.nextLine().trim();

                if (username.isEmpty()) {
                    System.out.println("Username cannot be empty. Exiting...");
                    return;
                }

                // System.out.println("Enter server IP address:");
                // String serverIP = scanner.nextLine().trim();

                if (serverIP.isEmpty()) {
                    System.out.println("Server IP cannot be empty. Exiting...");
                    return;
                }

                try (Socket socket = new Socket(serverIP, PORT)) {
                    System.out.println("Connected to the server.");
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());

                    out.writeUTF("CONNECT");
                    out.writeUTF(username);
                    out.flush();

                    String welcome = in.readUTF();
                    System.out.println(welcome);

                    while (true) {
                        System.out.println("\nChoose an option:");
                        System.out.println("1. Upload file");
                        System.out.println("2. Download file");
                        System.out.println("3. List files");
                        System.out.println("4. Delete file");
                        System.out.println("5. Exit");

                        String choice = scanner.nextLine().trim();
                        out.writeUTF(choice);
                        out.flush();

                        switch (choice) {
                            case "1":
                                uploadFile(scanner, in, out);
                                break;
                            case "2":
                                downloadFile(scanner, in, out);
                                break;
                            case "3":
                                listFiles(in);
                                break;
                            case "4":
                                deleteFile(scanner, in, out);
                                break;
                            case "5":
                                System.out.println("Exiting...");
                                return;
                            default:
                                System.out.println("Invalid option.");
                                String response = in.readUTF();
                                System.out.println("Server response: " + response);
                        }
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private static void uploadFile(Scanner scanner, DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Enter the file path to upload:");
        String filePath = scanner.nextLine().trim();
        File file = new File(filePath);

        if (!file.exists()) {
            System.out.println("File does not exist.");
            return;
        }

        out.writeUTF(file.getName());
        out.writeLong(file.length());
        out.flush();

        String serverResponse = in.readUTF();
        long startPosition = 0;
        
        if (serverResponse.startsWith("RESUME:")) {
            startPosition = Long.parseLong(serverResponse.split(":")[1]);
            System.out.println("Resuming upload from position: " + startPosition);
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            raf.seek(startPosition);
            byte[] buffer = new byte[CHUNK_SIZE];
            long totalBytesUploaded = startPosition;
            long fileSize = file.length();

            while (totalBytesUploaded < fileSize) {
                int bytesRead = raf.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesUploaded));
                if (bytesRead == -1) break;
                
                out.write(buffer, 0, bytesRead);
                totalBytesUploaded += bytesRead;
                resumeManager.updateResumeState(file.getName(), totalBytesUploaded);
                
                printProgressBar(totalBytesUploaded, fileSize);
            }

            out.flush();
            String completion = in.readUTF();
            System.out.println("\nServer response: " + completion);
            
            if (completion.equals("File uploaded successfully.")) {
                resumeManager.clearResumeState(file.getName());
            }
        }
    }

    private static void downloadFile(Scanner scanner, DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Enter the name of the file to download:");
        String fileName = scanner.nextLine().trim();
        out.writeUTF(fileName);
        out.flush();

        String serverResponse = in.readUTF();
        if (serverResponse.equals("FILE_NOT_FOUND")) {
            System.out.println("File not found on server.");
            return;
        }

        long fileSize = in.readLong();
        File outputFile = new File(DOWNLOAD_DIRECTORY, fileName);
        createDownloadDirectory();

        long downloadedBytes = resumeManager.getResumeState(fileName);
        
        if (downloadedBytes > 0) {
            System.out.println("Resuming download from: " + downloadedBytes + " bytes");
            out.writeUTF("RESUME:" + downloadedBytes);
        } else {
            out.writeUTF("START");
        }
        out.flush();

        try (RandomAccessFile raf = new RandomAccessFile(outputFile, "rw")) {
            raf.seek(downloadedBytes);
            byte[] buffer = new byte[CHUNK_SIZE];
            long totalBytesDownloaded = downloadedBytes;

            while (totalBytesDownloaded < fileSize) {
                int bytesRead = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalBytesDownloaded));
                if (bytesRead == -1) break;
                
                raf.write(buffer, 0, bytesRead);
                totalBytesDownloaded += bytesRead;
                resumeManager.updateResumeState(fileName, totalBytesDownloaded);
                
                printProgressBar(totalBytesDownloaded, fileSize);
            }

                    // Suite de la méthode downloadFile
            System.out.println("\nFile download complete.");
            resumeManager.clearResumeState(fileName);
        }
    }
    private static void listFiles(DataInputStream in) throws IOException {
        int fileCount = in.readInt();
        if (fileCount == 0) {
            System.out.println("No files found on server.");
            return;
        }

        System.out.println("\nFiles available on server:");
        for (int i = 0; i < fileCount; i++) {
            String fileName = in.readUTF();
            System.out.println((i + 1) + ". " + fileName);
        }
    }

    private static void deleteFile(Scanner scanner, DataInputStream in, DataOutputStream out) throws IOException {
        System.out.println("Enter the name of the file to delete:");
        String fileName = scanner.nextLine().trim();
        out.writeUTF(fileName);
        out.flush();

        String response = in.readUTF();
        switch (response) {
            case "PERMISSION_DENIED":
                System.out.println("Permission denied. You can only delete your own files.");
                break;
            case "FILE_NOT_FOUND":
                System.out.println("File not found on server.");
                break;
            case "DELETION_SUCCESS":
                System.out.println("File deleted successfully.");
                resumeManager.clearResumeState(fileName);
                break;
            case "DELETION_FAILED":
                System.out.println("Failed to delete the file.");
                break;
        }
    }

    private static void createDownloadDirectory() {
        File dir = new File(DOWNLOAD_DIRECTORY);
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    private static void printProgressBar(long current, long total) {
        int barLength = 50;
        double progress = (double) current / total;
        int completed = (int) (progress * barLength);
        
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            if (i < completed) {
                bar.append("=");
            } else if (i == completed) {
                bar.append(">");
            } else {
                bar.append(" ");
            }
        }
        bar.append("]");
        
        System.out.print("\r" + bar + String.format(" %.1f%%", progress * 100));
    }
}
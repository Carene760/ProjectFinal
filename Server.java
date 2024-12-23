import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Server {
    private static Properties config = new Properties();
    private static int PORT;
    private static String UPLOAD_DIR;
    private static String USER_FILE;
    private static String METADATA_FILE;
    private static ResumeManager resumeManager;

    static {
        try {
            FileInputStream input = new FileInputStream("file-config.txt");
            config.load(input);
            PORT = Integer.parseInt(config.getProperty("SERVER_PORT"));
            UPLOAD_DIR = config.getProperty("SERVER_DOWNLOAD_DIR");
            USER_FILE = "users.txt";
            METADATA_FILE = "file-metadata.txt";
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            resumeManager = new ResumeManager(config.getProperty("RESUME_TOKEN_FILE"));
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("Server started on port " + PORT);
                createDirectory(UPLOAD_DIR);
                createFile(USER_FILE);
                createFile(METADATA_FILE);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket).start();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    private static void createDirectory(String dir) {
        try {
            Files.createDirectories(Paths.get(dir));
        } catch (IOException e) {
            System.err.println("Error creating directory: " + e.getMessage());
        }
    }

    private static void createFile(String filename) {
        try {
            File file = new File(filename);
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Error creating file: " + e.getMessage());
        }
    }

    private static synchronized void addUser(String username) throws IOException {
        List<String> users = Files.readAllLines(Paths.get(USER_FILE));
        if (!users.contains(username)) {
            Files.write(Paths.get(USER_FILE), (username + "\n").getBytes(), StandardOpenOption.APPEND);
        }
    }

    private static class ClientHandler extends Thread {
        private final Socket clientSocket;
        private DataInputStream in;
        private DataOutputStream out;

        public ClientHandler(Socket socket) {
            this.clientSocket = socket;
        }

        @Override
        public void run() {
            try {
                in = new DataInputStream(clientSocket.getInputStream());
                out = new DataOutputStream(clientSocket.getOutputStream());

                String serverMessage = in.readUTF();
                String username = in.readUTF();
                addUser(username);

                out.writeUTF("Welcome, " + username);

                while (true) {
                    String command = in.readUTF();
                    switch (command.toLowerCase()) {
                        case "1":
                            handleUpload(username);
                            break;
                        case "2":
                            handleDownload();
                            break;
                        case "3":
                            handleList();
                            break;
                        case "4":
                            handleDelete(username);
                            break;
                        case "5":
                            out.writeUTF("Goodbye!");
                            return;
                        default:
                            out.writeUTF("Invalid command.");
                    }
                }
            } catch (IOException e) {
                System.err.println("Client disconnected: " + e.getMessage());
            } finally {
                try {
                    if (in != null) in.close();
                    if (out != null) out.close();
                    clientSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing resources: " + e.getMessage());
                }
            }
        }

        private void handleUpload(String username) throws IOException {
            String fileName = in.readUTF();
            long fileSize = in.readLong();

            File userDir = new File(UPLOAD_DIR, username);
            createDirectory(userDir.getAbsolutePath());
            File file = new File(userDir, fileName);

            long resumePosition = resumeManager.getResumeState(fileName);
            
            if (resumePosition > 0 && file.exists()) {
                out.writeUTF("RESUME:" + resumePosition);
            } else {
                out.writeUTF("START");
                resumePosition = 0;
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.seek(resumePosition);
                byte[] buffer = new byte[4096];
                long totalReceived = resumePosition;

                while (totalReceived < fileSize) {
                    int read = in.read(buffer, 0, (int) Math.min(buffer.length, fileSize - totalReceived));
                    if (read == -1) break;
                    
                    raf.write(buffer, 0, read);
                    totalReceived += read;
                    resumeManager.updateResumeState(fileName, totalReceived);
                }
            }

            resumeManager.clearResumeState(fileName);
            appendMetadata(username, fileName);
            out.writeUTF("File uploaded successfully.");
        }

        private void handleDownload() throws IOException {
            String fileName = in.readUTF();
            File file = findFile(fileName);

            if (file == null) {
                out.writeUTF("FILE_NOT_FOUND");
                return;
            }

            out.writeUTF("FILE_FOUND");
            out.writeLong(file.length());

            String clientResponse = in.readUTF();
            long startPosition = 0;
            
            if (clientResponse.startsWith("RESUME:")) {
                startPosition = Long.parseLong(clientResponse.split(":")[1]);
            }

            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(startPosition);
                byte[] buffer = new byte[4096];
                long totalSent = startPosition;

                while (totalSent < file.length()) {
                    int read = raf.read(buffer, 0, (int) Math.min(buffer.length, file.length() - totalSent));
                    if (read == -1) break;
                    
                    out.write(buffer, 0, read);
                    totalSent += read;
                    resumeManager.updateResumeState(fileName, totalSent);
                }
            }

            resumeManager.clearResumeState(fileName);
            out.flush();
        }

        private void handleList() throws IOException {
            List<Path> fileList = new ArrayList<>();
            Files.walk(Paths.get(UPLOAD_DIR))
                .filter(Files::isRegularFile)
                .forEach(fileList::add);

            out.writeInt(fileList.size());
            for (Path file : fileList) {
                out.writeUTF(file.getFileName().toString());
            }
        }

        private void handleDelete(String username) throws IOException {
            String fileName = in.readUTF();

            if (!isOwner(username, fileName)) {
                out.writeUTF("PERMISSION_DENIED");
                return;
            }

            File file = findFile(fileName);
            if (file == null) {
                out.writeUTF("FILE_NOT_FOUND");
                return;
            }

            if (file.delete()) {
                removeMetadata(fileName);
                resumeManager.clearResumeState(fileName);
                out.writeUTF("DELETION_SUCCESS");
            } else {
                out.writeUTF("DELETION_FAILED");
            }
        }

        private File findFile(String fileName) {
            try {
                return Files.walk(Paths.get(UPLOAD_DIR))
                    .filter(Files::isRegularFile)
                    .map(Path::toFile)
                    .filter(f -> f.getName().equals(fileName))
                    .findFirst()
                    .orElse(null);
            } catch (IOException e) {
                return null;
            }
        }

        private boolean isOwner(String username, String fileName) throws IOException {
            return Files.lines(Paths.get(METADATA_FILE))
                .anyMatch(line -> {
                    String[] parts = line.split(",");
                    return parts.length == 2 && parts[0].equals(username) && parts[1].equals(fileName);
                });
        }

        private synchronized void appendMetadata(String username, String fileName) throws IOException {
            String entry = username + "," + fileName + "\n";
            Files.write(Paths.get(METADATA_FILE), entry.getBytes(), StandardOpenOption.APPEND);
        }

        private synchronized void removeMetadata(String fileName) throws IOException {
            List<String> metadata = Files.readAllLines(Paths.get(METADATA_FILE));
            metadata.removeIf(entry -> entry.split(",")[1].equals(fileName));
            Files.write(Paths.get(METADATA_FILE), metadata);
        }
    }
}

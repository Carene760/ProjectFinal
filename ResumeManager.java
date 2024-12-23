import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class ResumeManager {
    private final File resumeFile;
    private final Map<String, Long> resumeStates;

    public ResumeManager(String resumeFilePath) throws IOException {
        this.resumeFile = new File(resumeFilePath);
        this.resumeStates = new HashMap<>();
        loadResumeStates();
    }

    private void loadResumeStates() throws IOException {
        if (!resumeFile.exists()) {
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(resumeFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(" ");
                if (parts.length == 2) {
                    resumeStates.put(parts[0], Long.parseLong(parts[1]));
                }
            }
        }
    }

    public long getResumeState(String fileName) {
        return resumeStates.getOrDefault(fileName, 0L);
    }

    public void updateResumeState(String fileName, long bytesTransferred) throws IOException {
        resumeStates.put(fileName, bytesTransferred);
        saveResumeStates();
    }

    public void clearResumeState(String fileName) throws IOException {
        resumeStates.remove(fileName);
        saveResumeStates();
    }

    private void saveResumeStates() throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resumeFile))) {
            for (Map.Entry<String, Long> entry : resumeStates.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        }
    }
}
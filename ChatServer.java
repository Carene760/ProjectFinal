import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.text.SimpleDateFormat;

public class ChatServer {
    private static String CONFIG_FILE = "chat-config.txt";
    private static int PORT;
    private static Set<ClientHandler> clientHandlers = new CopyOnWriteArraySet<>();
    private static Map<String, Message> messages = new HashMap<>(); // ID -> Message
    private static final File HISTORY_DIR = new File("History");

    public static void main(String[] args) {
        // Créer le répertoire History s'il n'existe pas
        if (!HISTORY_DIR.exists()) {
            HISTORY_DIR.mkdirs();
        }

        loadConfig();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Serveur de chat démarré sur le port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nouveau client connecté : " + clientSocket.getInetAddress());

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clientHandlers.add(clientHandler);
                new Thread(clientHandler).start();

                // Charger l'historique actuel à l'arrivée d'un nouvel utilisateur
                String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String history = loadHistory(today);
                if (!history.isEmpty()) {
                    clientHandler.sendMessage("Historique de la journée:\n" + history);
                }
            }
        } catch (IOException e) {
            System.err.println("Erreur du serveur : " + e.getMessage());
        }
    }

    private static void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            Properties prop = new Properties();
            prop.load(input);

            PORT = Integer.parseInt(prop.getProperty("SERVER_PORT"));
        } catch (IOException ex) {
            System.err.println("Erreur lors du chargement du fichier de configuration.");
        }
    }

    private static String loadAndMergeHistory(String date) {
        File historyFile = new File(HISTORY_DIR, "history_" + date + ".txt");
        if (!historyFile.exists()) {
            return "";
        }

        try {
            return new String(java.nio.file.Files.readAllBytes(historyFile.toPath()));
        } catch (IOException e) {
            return "Erreur lors de la lecture de l'historique : " + e.getMessage();
        }
    }

    private static String loadHistory(String date) {
        // Chargement des fichiers d'historique
        File baseFile = new File(HISTORY_DIR, "history_" + date + ".txt");
        File modifiedFile = new File(HISTORY_DIR, "history_modified_" + date + ".txt");
        File deletedFile = new File(HISTORY_DIR, "history_deleted_" + date + ".txt");
    
        Map<String, String> finalHistory = new LinkedHashMap<>(); // ID -> Message contenu
        Set<String> deletedMessages = new HashSet<>(); // IDs des messages supprimés
    
        // Lecture des messages de base
        if (baseFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(baseFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 5);
                    if (parts.length >= 5) {
                        String messageId = parts[0];
                        String sender = parts[2];
                        String status = parts[3];
                        String content = parts[4];
    
                        if (!"deleted".equals(status)) { // Ignorer les messages supprimés
                            finalHistory.put(messageId, sender + ": " + content);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de la lecture du fichier de base : " + e.getMessage());
            }
        }
    
        // Lecture des suppressions
        if (deletedFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(deletedFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 2);
                    if (parts.length >= 2) {
                        String messageId = parts[0];
                        deletedMessages.add(messageId); // Ajouter à la liste des IDs supprimés
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de la lecture du fichier des suppressions : " + e.getMessage());
            }
        }
    
        // Supprimer les messages qui ont été marqués comme supprimés
        for (String messageId : deletedMessages) {
            finalHistory.remove(messageId);
        }
    
        // Lecture des modifications
        if (modifiedFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(modifiedFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split(":", 5);
                    if (parts.length >= 5) {
                        String messageId = parts[0];
                        String newContent = parts[4];
                        // Remplacer le contenu dans l'historique de base
                        if (finalHistory.containsKey(messageId)) {
                            String sender = finalHistory.get(messageId).split(": ", 2)[0];
                            finalHistory.put(messageId, sender + ": " + newContent);
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("Erreur lors de la lecture du fichier des modifications : " + e.getMessage());
            }
        }
    
        // Construire le résultat final sous forme de chaîne
        StringBuilder mergedHistory = new StringBuilder();
        for (String message : finalHistory.values()) {
            mergedHistory.append(message).append("\n");
        }
    
        return mergedHistory.toString().trim();
    }
    
    

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private BufferedReader in;
        private String clientName;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);
        
                // Demander le nom de l'utilisateur
                out.println("Entrez votre nom :");
                clientName = in.readLine();  // Lecture du nom du client
        
                // Annonce que l'utilisateur a rejoint le chat
                broadcast(clientName + " a rejoint le chat.");
        
                // Envoi de l'historique du jour après que le nom soit saisi
                String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
                String history = loadHistory(today);
        
                // L'historique est envoyé **après** l'entrée du nom, pas avant
                if (!history.isEmpty()) {
                    this.sendMessage("Historique de la journée :\n" + history);
                }
        
                // Traitement des messages
                String message;
                while ((message = in.readLine()) != null) {
                    if (message.equalsIgnoreCase("bye")) {
                        break;
                    } else if (message.startsWith("MODIFY:")) {
                        handleModifyMessage(message);
                    } else if (message.startsWith("DELETE:")) {
                        handleDeleteMessage(message);
                    } else if (message.startsWith("HISTORY:")) {
                        String date = message.split(":")[1];
                        out.println(loadHistory(date));
                    } else {
                        handleNewMessage(message);
                    }
                }
        
                // Annonce que l'utilisateur a quitté le chat
                broadcast(clientName + " a quitté le chat.");
            } catch (IOException e) {
                System.err.println("Erreur avec le client : " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    System.err.println("Impossible de fermer le socket : " + e.getMessage());
                }
                clientHandlers.remove(this);
            }
        }
        

        private void handleNewMessage(String content) {
            String messageId = UUID.randomUUID().toString();
            Message newMessage = new Message(messageId, clientName, content);
            messages.put(messageId, newMessage);
            saveMessageToHistory(newMessage);
            broadcast(String.format("%s (ID: %s) : %s", clientName, messageId, content));
        }

        private void handleModifyMessage(String message) {
            String[] parts = message.split(":", 3);
            if (parts.length < 3) return;

            String messageId = parts[1];
            String newContent = parts[2];

            if (messages.containsKey(messageId)) {
                Message targetMessage = messages.get(messageId);
                if (targetMessage.isDeleted()) {
                    out.println("Erreur : Le message avec l'identifiant " + messageId + " a été supprimé et ne peut pas être modifié.");
                    return;
                }

                String oldContent = targetMessage.getContent();
                targetMessage.setContent(newContent);
                saveModifiedMessageToHistory(targetMessage, oldContent, newContent);
                broadcast(String.format("%s a modifié un message (ID: %s):%s", clientName, messageId, newContent));
            } else {
                out.println("Erreur : Message ID introuvable.");
            }
        }

        private void handleDeleteMessage(String message) {
            String[] parts = message.split(":", 2);
            if (parts.length < 2) return;

            String messageId = parts[1];

            if (messages.containsKey(messageId)) {
                Message targetMessage = messages.get(messageId);
                if (targetMessage.isDeleted()) {
                    out.println("Erreur : Le message avec l'identifiant " + messageId + " est déjà supprimé.");
                    return;
                }

                targetMessage.setDeleted();
                saveDeletedMessageToHistory(targetMessage);
                broadcast(String.format("%s a supprimé un message (ID: %s).", clientName, messageId));
            } else {
                out.println("Erreur : Message ID introuvable.");
            }
        }

        private void saveMessageToHistory(Message message) {
            File historyFile = new File(HISTORY_DIR, "history_" + getCurrentDate() + ".txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(historyFile, true))) {
                writer.println(formatMessage(message));
            } catch (IOException e) {
                System.err.println("Erreur lors de l'enregistrement de l'historique : " + e.getMessage());
            }
        }

        private void saveModifiedMessageToHistory(Message message, String oldContent, String newContent) {
            File modifiedHistoryFile = new File(HISTORY_DIR, "history_modified_" + getCurrentDate() + ".txt");
            try (PrintWriter writer = new PrintWriter(new FileWriter(modifiedHistoryFile, true))) {
                writer.println(String.format("%s:%d:%s:%s:%s", message.getId(), System.currentTimeMillis(), message.getSender(), oldContent, newContent));
            } catch (IOException e) {
                System.err.println("Erreur lors de l'enregistrement de l'historique des modifications : " + e.getMessage());
            }
        }

        private void saveDeletedMessageToHistory(Message message) {
            File deletedHistoryFile = new File(HISTORY_DIR, "history_deleted_" + getCurrentDate() + ".txt");
            System.out.println("Tentative d'écriture dans le fichier : " + deletedHistoryFile.getAbsolutePath());
        
            try {
                // Vérifier si le fichier peut être écrit
                if (!deletedHistoryFile.exists()) {
                    System.out.println("Le fichier n'existe pas, tentative de création.");
                    // Si le fichier n'existe pas, créez-le
                    deletedHistoryFile.createNewFile();
                }
        
                // Vérification des permissions avant l'écriture
                if (deletedHistoryFile.canWrite()) {
                    System.out.println("Permissions d'écriture validées.");
                } else {
                    System.out.println("Erreur : Pas de permission d'écriture sur le fichier.");
                }
        
                // Lire les lignes existantes pour éviter les doublons
                List<String> existingLines = java.nio.file.Files.readAllLines(deletedHistoryFile.toPath());
                System.out.println("Lignes existantes dans le fichier : " + existingLines.size());
        
                for (String line : existingLines) {
                    if (line.startsWith(message.getId() + ":")) {
                        System.out.println("Le message avec l'ID " + message.getId() + " est déjà dans l'historique.");
                        return; // Ne rien faire si l'entrée existe déjà
                    }
                }
        
                // Écrire dans le fichier
                try (PrintWriter writer = new PrintWriter(new FileWriter(deletedHistoryFile, true))) {
                    writer.println(String.format("%s:%d:%s:%s", message.getId(), System.currentTimeMillis(), message.getSender(), message.getContent()));
                    System.out.println("Message supprimé écrit dans l'historique.");
                }
        
            } catch (IOException e) {
                System.err.println("Erreur lors de l'enregistrement de l'historique des suppressions : " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        
        

        private String formatMessage(Message message) {
            return String.format("%s:%d:%s:%s:%s",
                message.getId(),
                message.getTimestamp(),
                message.getSender(),
                message.isDeleted() ? "deleted" : "normal",
                message.isDeleted() ? "null" : message.getContent()
            );
        }

        private String getCurrentDate() {
            return new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        }

        private void broadcast(String message) {
            for (ClientHandler client : clientHandlers) {
                client.out.println(message);
            }
        }

        public void sendMessage(String message) {
            out.println(message);
        }
        
    }

    static class Message {
        private final String id;
        private final String sender;
        private String content;
        private boolean isDeleted;
        private final long timestamp;

        public Message(String id, String sender, String content) {
            this.id = id;
            this.sender = sender;
            this.content = content;
            this.isDeleted = false;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() {
            return id;
        }

        public String getSender() {
            return sender;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public boolean isDeleted() {
            return isDeleted;
        }

        public void setDeleted() {
            this.isDeleted = true;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}


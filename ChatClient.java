import java.io.*;
import java.net.*;
import java.util.*;

public class ChatClient {
    private static final String CONFIG_FILE = "chat_config.txt";
    private static String SERVER_ADDRESS;
    private static int SERVER_PORT;

    public static void main(String[] args) {
        loadConfig();

        System.out.println("Options:");
        System.out.println("1 - Modify a message with command: MODIFY:<id_message>:new message");
        System.out.println("2 - Delete a message with command: DELETE:<id_message>");
        System.out.println("3 - View file history with command: HISTORY:<date>");
        System.out.println("4 - Quit with command: bye");

        try (Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner scanner = new Scanner(System.in)) {

            // Démarrer un thread pour recevoir les messages du serveur
            new Thread(() -> {
                try {
                    String serverMessage;
                    while ((serverMessage = in.readLine()) != null) {
                        System.out.println(serverMessage);
                    }
                } catch (IOException e) {
                    System.err.println("Erreur lors de la réception des messages : " + e.getMessage());
                }
            }).start();

            // Envoyer des messages au serveur
            while (true) {
                String userMessage = scanner.nextLine();

                if (userMessage.equalsIgnoreCase("bye")) {
                    out.println(userMessage);
                    break;
                }

                // Envoyer les autres commandes (MODIFY, DELETE, HISTORY, etc.)
                if (userMessage.startsWith("MODIFY:") || userMessage.startsWith("DELETE:") || userMessage.startsWith("HISTORY:")) {
                    out.println(userMessage);
                } else {
                    out.println(userMessage);
                }
            }

            System.out.println("Vous avez quitté le chat.");
        } catch (IOException e) {
            System.err.println("Erreur de connexion au serveur : " + e.getMessage());
        }
    }

    private static void loadConfig() {
        try (InputStream input = new FileInputStream(CONFIG_FILE)) {
            Properties prop = new Properties();
            prop.load(input);

            SERVER_ADDRESS = prop.getProperty("SERVER_ADDRESS");
            SERVER_PORT = Integer.parseInt(prop.getProperty("SERVER_PORT"));
        } catch (IOException ex) {
            System.err.println("Erreur lors du chargement du fichier de configuration.");
        }
    }
}

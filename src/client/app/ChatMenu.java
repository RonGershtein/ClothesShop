// ============================================================================
// Simple console chat client. Connects to the chat server, prints incoming
// messages, and sends whatever the user types.
// ============================================================================

package client.app;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatMenu {
    private final Scanner in; // Reads user input from console

    // ------------------------------------------------------------------------
    // Constructor: keeps a Scanner for reading user input from System.in.
    // No network work here; just store the reference for later use.
    // ------------------------------------------------------------------------
    public ChatMenu(Scanner in) { this.in = in; }

    // ------------------------------------------------------------------------
    // loop(): connects to the chat server and runs the chat session.
    // Opens a TCP socket, starts a background reader to print server lines,
    // and forwards each console line to the server until the user types "/quit".
    // ------------------------------------------------------------------------
    public void loop() {
        // try-with-resources: socket and streams are auto-closed on exit
        try (Socket s = new Socket("127.0.0.1", 6060);
             BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream()), true)) {

            System.out.println("Connected to chat. Type messages; /quit to exit.");

            // Background thread: continuously read lines from server and print them.
            // If the server closes the connection or an error happens, the loop ends silently.
            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("Peer: " + line); // show any incoming server/peer message
                    }
                } catch (IOException ignored) {
                    // Keep it simple for console use; no detailed error handling here.
                }
            });
            reader.setDaemon(true); // Daemon so it won't block JVM shutdown if main loop ends.
            reader.start();

            // Main input loop: read a line from the console and send it to the server.
            // This is a "raw" client: it sends exactly what the user types.
            while (true) {
                String message = in.nextLine();        // wait for user input
                if ("/quit".equalsIgnoreCase(message)) // local exit command for the console client
                    break;
                pw.println(message);                    // forward the text line to the server
            }
        } catch (Exception e) {
            // Basic error message to keep the student console straightforward.
            System.out.println("Chat error: " + e.getMessage());
        }
    }
}

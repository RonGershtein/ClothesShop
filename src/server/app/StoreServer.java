// ============================================================================
// Simple TCP store server. Accepts client connections and delegates each
// connection to ClientHandler using a cached thread pool. Exposes services for
// auth, inventory, customers, and sales.
// ============================================================================

package server.app;

import server.domain.employees.AuthService;
import server.domain.invantory.InventoryService;
import server.domain.customers.CustomerService;
import server.domain.sales.SalesService;

import server.net.ClientHandler;
import server.util.Loggers;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StoreServer {
    // Core configuration and services used by ClientHandler
    private final int port;                             // TCP port to listen on
    private final AuthService auth = new AuthService(); // authentication & sessions
    private final InventoryService inventory = new InventoryService(); // products & stock
    private final CustomerService customers = new CustomerService();   // customer records
    private final SalesService sales = new SalesService();             // sales logic
    private final ExecutorService pool = Executors.newCachedThreadPool(); // handles clients concurrently

    // ------------------------------------------------------------------------
    // Constructor: stores the port and installs a shutdown hook.
    // The hook attempts to stop the thread pool when the JVM is shutting down.
    // ------------------------------------------------------------------------
    public StoreServer(int port) {
        this.port = port;
        // Close the thread pool when the process is shutting down
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { pool.shutdownNow(); } catch (Exception ignored) {}
        }));
    }

    // ------------------------------------------------------------------------
    // main(): entry point. Parses port from args (default 5050) and starts server.
    // ------------------------------------------------------------------------
    public static void main(String[] args) {
        int port = 5050;
        if (args != null && args.length > 0) {
            try { port = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        new StoreServer(port).start();
    }

    // ------------------------------------------------------------------------
    // start(): opens a ServerSocket and accepts clients in a loop.
    // Each accepted socket is handed to ClientHandler on the thread pool.
    // Logs fatal errors and always tries to shut down the pool at the end.
    // ------------------------------------------------------------------------
    /** Starts the server loop and handles clients. Never throws to caller; logs on error. */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Loggers.system().info("StoreServer started on port " + port);
            while (true) {
                Socket socket = serverSocket.accept(); // blocks until a client connects
                // Handle the client on a worker thread (multithreading)
                pool.submit(new ClientHandler(socket, auth, inventory, customers, sales));
            }
        } catch (IOException e) {
            Loggers.system().severe("StoreServer fatal error: " + e.getMessage());
        } finally {
            try { pool.shutdownNow(); } catch (Exception ignored) {}
        }
    }
}

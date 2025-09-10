// ============================================================================
// Console client application for the shop network.
// Shows numeric menus, connects to StoreServer (inventory, sales, customers)
// and to ChatServer (branch-to-branch chat). Reads input from console and
// sends text commands to the servers.
// ============================================================================

package client.app;

import server.domain.employees.EmployeeDirectory;
import server.domain.employees.Employee;
import server.domain.employees.PasswordPolicy;
import server.domain.employees.AuthService;
import server.shared.Branch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Console client with numeric menus.
 * Connects to StoreServer (127.0.0.1:5050) and uses text protocol:
 *   LOGIN, LOGOUT, LIST, BUY, SELL, ADD_PRODUCT, REMOVE_PRODUCT,
 *   CUSTOMER_ADD, CUSTOMER_LIST, SELL_MULTI
 * Also supports ChatServer (127.0.0.1:6060) for chat.
 */
public class ClientConsole {

    private static final String HOST = "127.0.0.1";
    private static final int STORE_PORT = 5050;
    private static final int CHAT_PORT = 6060; // ChatServer should run here

    private final Scanner in = new Scanner(System.in);
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    private String loggedUsername = null;
    private String role = null;             // "admin" or "employee"
    private Branch employeeBranch = null;   // set for employees
    private String employeeRole = null;     // SALESPERSON/CASHIER/SHIFT_MANAGER (info only)

    // ------------------------------------------------------------------------
    // main(): entry point for the console client.
    // Creates a ClientConsole and starts the interactive run loop.
    // ------------------------------------------------------------------------
    public static void main(String[] args) {
        new ClientConsole().run();
    }

    // ------------------------------------------------------------------------
    // run(): top-level workflow of the client.
    // Connects to the store, performs login, then routes to admin/employee menu.
    // ------------------------------------------------------------------------
    public void run() {
        System.out.println("=== Shop Network Client ===");
        try {
            connectToStore();
            String welcome = reader.readLine(); // "OK WELCOME"
            if (welcome != null) System.out.println(welcome);

            if (!loginScreen()) {
                System.out.println("Login failed. Exiting.");
                return;
            }

            if ("admin".equals(role)) {
                adminMenuLoop();
            } else {
                employeeMenuLoop();
            }

        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
        } finally {
            safeCloseStore();
        }
    }

    // -------------------- Store connection --------------------

    // ------------------------------------------------------------------------
    // connectToStore(): opens a TCP socket to the StoreServer and prepares I/O.
    // Initializes reader/writer for the text protocol.
    // ------------------------------------------------------------------------
    private void connectToStore() throws IOException {
        socket = new Socket(HOST, STORE_PORT);
        reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()), true);
    }

    // ------------------------------------------------------------------------
    // safeCloseStore(): closes the store socket gracefully.
    // Flushes the writer and closes the socket if still open.
    // ------------------------------------------------------------------------
    private void safeCloseStore() {
        try {
            if (writer != null) writer.flush();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException ignored) {
        }
    }

    // -------------------- Login (auto role detection) --------------------

    // ------------------------------------------------------------------------
    // loginScreen(): interactive login loop.
    // Collects username/password, sends LOGIN, and sets local role/branch info.
    // ------------------------------------------------------------------------
    private boolean loginScreen() throws IOException {
        while (true) {
            System.out.println("\n=== Login ===");
            System.out.print("Username: ");
            String username = in.nextLine().trim();
            String password = readPassword("Password: ");

            role = ("admin".equalsIgnoreCase(username) && "admin".equals(password)) ? "admin" : "employee";

            writer.println("LOGIN " + username + " " + password + " " + role);
            String resp = reader.readLine();
            if (resp != null && resp.startsWith("OK LOGIN")) {
                loggedUsername = username;

                if ("employee".equals(role)) {
                    EmployeeDirectory directory = new EmployeeDirectory();
                    Employee rec = directory.findByUsername(username)
                            .orElseThrow(() -> new IllegalStateException("Employee not found in directory"));
                    employeeBranch = rec.branch();
                    employeeRole = rec.role();
                    System.out.println("Login successful as " + prettyEmployeeRole(employeeRole));
                    System.out.println("Your branch: " + employeeBranch);
                } else {
                    System.out.println("Login successful as ADMIN");
                }
                return true;
            } else {
                System.out.println(resp == null ? "Login failed." : resp);

                if (resp != null && resp.equals("ERR LOGIN ALREADY_CONNECTED")) {
                    System.out.println("Already connected. Please logout from other sessions first.");
                } else {
                    System.out.println("Invalid credentials. Please try again.");
                }

                System.out.print("Try again? (y/n): ");
                String retry = in.nextLine().trim().toLowerCase();
                if (!retry.equals("y") && !retry.equals("yes")) {
                    System.out.println("Login cancelled by user.");
                    return false;
                }
            }
        }
    }

    // ------------------------------------------------------------------------
    // readPassword(): reads a password string from console.
    // Simple visible input (no masking) to keep it straightforward for students.
    // ------------------------------------------------------------------------
    private String readPassword(String prompt) {
        System.out.print(prompt);
        return in.nextLine();
    }

    // -------------------- Admin Menu --------------------

    // ------------------------------------------------------------------------
    // adminMenuLoop(): main menu for admin operations.
    // Lets admin list inventory, manage employees, configure password policy, and view logs.
    // ------------------------------------------------------------------------
    private void adminMenuLoop() throws IOException {
        EmployeeDirectory directory = new EmployeeDirectory();

        while (true) {
            System.out.println("\n-- Admin Menu --");
            System.out.println("1) List inventory (choose branch)");
            System.out.println("2) Add employee");
            System.out.println("3) Set password policy");
            System.out.println("4) List employees");
            System.out.println("5) Delete employee by ID");
            System.out.println("6) Logs & Reports");
            System.out.println("0) Logout");
            System.out.print("Choice: ");
            String c = in.nextLine().trim();

            switch (c) {
                case "0":
                    logout();
                    return;
                case "1":
                    Branch branch = askBranch();
                    listInventory(branch);
                    break;
                case "2":
                    addEmployeeFlow(directory);
                    break;
                case "3":
                    setPasswordPolicyFlow();
                    break;
                case "4":
                    listEmployeesFlow(directory);
                    break;
                case "5":
                    deleteEmployeeFlow(directory);
                    break;
                case "6":
                    adminLogsMenu();
                    break;
                default:
                    System.out.println("Invalid choice.");
            }
        }
    }

    // ------------------------------------------------------------------------
    // addEmployeeFlow(): collects fields and adds a new employee record.
    // Validates password against current policy, hashes it, and saves via directory.
    // ------------------------------------------------------------------------
    private void addEmployeeFlow(EmployeeDirectory directory) {
        try {
            System.out.println("\n=== Add Employee ===");
            System.out.print("Username: ");
            String username = in.nextLine().trim();

            String password = readPassword("Password: ");
            String confirm = readPassword("Confirm password: ");
            if (!password.equals(confirm)) {
                System.out.println("Passwords do not match.");
                return;
            }

            // --- Enforce password policy BEFORE hashing & saving ---
            int min = PasswordPolicy.minimumLength();
            boolean needSpecial = PasswordPolicy.requireSpecialChar(); // mapped to "require special char"
            boolean needLetter = PasswordPolicy.requireLetter();

            String policyError = null;
            if (password.length() < min) {
                policyError = "Too short (min " + min + ")";
            } else if (needSpecial && !password.matches(".*[^A-Za-z0-9].*")) {
                policyError = "Must contain at least one special char (e.g. !@#$%)";
            } else if (needLetter && !password.matches(".*[A-Za-z].*")) {
                policyError = "Must contain at least one letter";
            }
            if (policyError != null) {
                System.out.println("Password does not meet the current policy: " + policyError);
                displayPasswordPolicy();
                return;
            }
            // -------------------------------------------------------

            String roleCode = askRole();     // SALESPERSON/CASHIER/SHIFT_MANAGER
            Branch branch = askBranch();     // HOLON/TEL_AVIV/RISHON

            System.out.print("Account number: ");
            String accountNumber = in.nextLine().trim();
            System.out.print("Phone: ");
            String phone = in.nextLine().trim();

            System.out.print("Full name: ");
            String fullName = in.nextLine().trim();
            System.out.print("National ID: ");
            String nationalId = in.nextLine().trim();

            // Hash only AFTER policy passed
            String passwordHash = AuthService.sha256(password);

            Employee rec = directory.addEmployee(
                    username, passwordHash, roleCode, branch, accountNumber, phone, fullName, nationalId
            );

            System.out.println("Employee added. ID=" + rec.employeeId()
                    + ", Username=" + rec.username()
                    + ", Role=" + rec.role()
                    + ", Branch=" + rec.branch()
                    + ", FullName=" + rec.fullName()
                    + ", NationalID=" + rec.nationalId());

        } catch (IllegalArgumentException ex) {
            System.out.println("Failed: " + ex.getMessage());
            String msg = ex.getMessage();
            if (msg != null && msg.toLowerCase().contains("password")) {
                displayPasswordPolicy();
            }
        } catch (Exception e) {
            System.out.println("Failed: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // deleteEmployeeFlow(): deletes an employee record by ID.
    // Confirms with the user before deletion and prints result status.
    // ------------------------------------------------------------------------
    private void deleteEmployeeFlow(EmployeeDirectory directory) {
        System.out.println("\n=== Delete Employee ===");
        System.out.print("Employee ID to delete: ");
        String id = in.nextLine().trim();

        java.util.Optional<Employee> recOpt = directory.findById(id);
        if (recOpt.isEmpty()) {
            System.out.println("Employee not found: " + id);
            return;
        }
        Employee r = recOpt.get();
        displayEmployeeDeleteConfirmation(r);
        if (!askYesNo("Are you sure? (y/n): ")) {
            System.out.println("Cancelled.");
            return;
        }
        boolean ok = directory.deleteById(id);
        System.out.println(ok ? "Employee deleted." : "Delete failed.");
    }

    // ------------------------------------------------------------------------
    // setPasswordPolicyFlow(): updates and saves the password policy.
    // Asks for minimal length and boolean flags; writes through PasswordPolicy.
    // ------------------------------------------------------------------------
    private void setPasswordPolicyFlow() {
        System.out.println("\n=== Password Policy ===");
        displayPasswordPolicy();

        int min = askPositiveInt("New minimum length (>=1): ");
        boolean needSpecial = askYesNo("Require special char (e.g. !@#$%)? (y/n): ");
        boolean needLetter = askYesNo("Require letter? (y/n): ");

        // Pass needSpecial into the existing 'requireSpecialChar' slot (mapped to "special")
        PasswordPolicy.configure(min, needSpecial, needLetter);
        System.out.println("Policy updated and saved.");
    }

    // ------------------------------------------------------------------------
    // listEmployeesFlow(): shows the current employee directory as a table.
    // Uses EmployeeDirectory to fetch all records and prints formatted output.
    // ------------------------------------------------------------------------
    private void listEmployeesFlow(EmployeeDirectory directory) {
        displayEmployeesTable(directory.listAll());
    }

    // ------------------------------------------------------------------------
    // displayEmployeesTable(): prints a formatted table of employees.
    // Aligns columns and shows a total count at the end.
    // ------------------------------------------------------------------------
    private void displayEmployeesTable(java.util.List<Employee> employees) {
        if (employees.isEmpty()) {
            System.out.println("No employees found.");
            return;
        }

        System.out.println("\n" + "=".repeat(105));
        System.out.println("                                  EMPLOYEE DIRECTORY");
        System.out.println("=".repeat(105));

        System.out.printf("%-5s %-15s %-18s %-12s %-15s %-15s %-20s %-12s%n",
                "ID", "Username", "Role", "Branch", "Account", "Phone", "Full Name", "National ID");
        System.out.println("-".repeat(105));

        for (Employee r : employees) {
            String role = prettyEmployeeRole(r.role());
            System.out.printf("%-5s %-15s %-18s %-12s %-15s %-15s %-20s %-12s%n",
                    r.employeeId(), r.username(), role, r.branch(), r.accountNumber(), r.phone(), r.fullName(), r.nationalId());
        }

        System.out.println("-".repeat(105));
        System.out.println("Total employees: " + employees.size());
        System.out.println("=".repeat(105) + "\n");
    }

    // ------------------------------------------------------------------------
    // askRole(): asks the user to choose a role from a numeric menu.
    // Returns the role code string used by the server.
    // ------------------------------------------------------------------------
    private String askRole() {
        while (true) {
            System.out.println("Select role:");
            System.out.println("1) SALESPERSON");
            System.out.println("2) CASHIER");
            System.out.println("3) SHIFT_MANAGER");
            System.out.print("Choice: ");
            String c = in.nextLine().trim();
            switch (c) {
                case "1" -> {
                    return "SALESPERSON";
                }
                case "2" -> {
                    return "CASHIER";
                }
                case "3" -> {
                    return "SHIFT_MANAGER";
                }
            }
            System.out.println("Invalid choice.");
        }
    }

    // -------------------- Employee Menu --------------------

    // ------------------------------------------------------------------------
    // employeeMenuLoop(): main menu for employees.
    // Allows selling, ordering stock, listing inventory, customers submenu, and chat.
    // ------------------------------------------------------------------------
    private void employeeMenuLoop() throws IOException {
        while (true) {
            System.out.println("\n-- Employee Menu (" + loggedUsername + ", " + employeeBranch + ", " + prettyEmployeeRole(employeeRole) + ") --");
            System.out.println("1) Sell Product");
            System.out.println("2) Order product to branch");
            System.out.println("3) List inventory (my branch)");
            System.out.println("4) Customers");
            System.out.println("5) Chat");
            System.out.println("0) Logout");
            System.out.print("Choice: ");
            String c = in.nextLine().trim();

            switch (c) {
                case "0" -> {
                    logout();
                    return;
                }
                case "1" -> sellCart(employeeBranch);
                case "2" -> orderStockToBranch(employeeBranch);
                case "3" -> listInventory(employeeBranch);
                case "4" -> customersMenu();
                case "5" -> startChatClient();
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // -------------------- Customers submenu --------------------

    // ------------------------------------------------------------------------
    // customersMenu(): submenu for customer actions.
    // Routes to add/list customers or returns back to the previous menu.
    // ------------------------------------------------------------------------
    private void customersMenu() throws IOException {
        while (true) {
            System.out.println("\n-- Customers --");
            System.out.println("1) Add customer");
            System.out.println("2) List customers");
            System.out.println("0) Back");
            System.out.print("Choice: ");
            String c = in.nextLine().trim();
            switch (c) {
                case "0" -> {
                    return;
                }
                case "1" -> doAddCustomer();
                case "2" -> listInventoryCustomers();
                default -> System.out.println("Invalid choice.");
            }
        }
    }

    // ------------------------------------------------------------------------
    // doAddCustomer(): collects minimal fields and sends CUSTOMER_ADD command.
    // Prints the server response after replacing underscores with spaces.
    // ------------------------------------------------------------------------
    private void doAddCustomer() throws IOException {
        System.out.println("\n=== Add Customer ===");
        System.out.print("ID (national ID): ");
        String id = in.nextLine().trim();

        System.out.print("Full name: ");
        String fullName = in.nextLine().trim();

        System.out.print("Phone: ");
        String phone = in.nextLine().trim();

        String type = "NEW"; // Default to NEW customer type

        String nameToken = fullName.replace(' ', '_');
        writer.println("CUSTOMER_ADD " + id + " " + nameToken + " " + phone + " " + type);
        String resp = reader.readLine();
        System.out.println(resp == null ? "No response" : resp.replace('_', ' '));
    }

    // ------------------------------------------------------------------------
    // listInventoryCustomers(): requests all customers and prints a table.
    // Reads lines until "OK END", collects "CUST ..." entries for rendering.
    // ------------------------------------------------------------------------
    private void listInventoryCustomers() throws IOException {
        writer.println("CUSTOMER_LIST");
        String line;

        java.util.List<String> customers = new java.util.ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if ("OK END".equals(line)) break;
            if (line.startsWith("CUST ")) {
                customers.add(line.substring(5));
            }
        }

        if (customers.isEmpty()) {
            System.out.println("No customers found.");
            return;
        }

        displayCustomersTable(customers);
    }

    // ------------------------------------------------------------------------
    // displayCustomersTable(): prints a simple table of customer data.
    // Splits the CSV-like line into columns: ID, Full Name, Phone, Type.
    // ------------------------------------------------------------------------
    private void displayCustomersTable(java.util.List<String> customers) {
        System.out.println("\n" + "=".repeat(75));
        System.out.println("                              CUSTOMER DIRECTORY");
        System.out.println("=".repeat(75));

        System.out.printf("%-15s %-25s %-15s %-12s%n",
                "ID", "Full Name", "Phone", "Type");
        System.out.println("-".repeat(75));

        for (String customer : customers) {
            String[] parts = customer.split(",");
            if (parts.length >= 4) {
                String id = parts[0];
                String fullName = parts[1];
                String phone = parts[2];
                String type = parts[3];

                System.out.printf("%-15s %-25s %-15s %-12s%n",
                        id, fullName, phone, type);
            }
        }

        System.out.println("-".repeat(75));
        System.out.println("Total customers: " + customers.size());
        System.out.println("=".repeat(75) + "\n");
    }

    // ------------------------------------------------------------------------
    // displayEmployeeDeleteConfirmation(): shows the record about to be deleted.
    // Helps the admin visually confirm the correct employee.
    // ------------------------------------------------------------------------
    private void displayEmployeeDeleteConfirmation(Employee r) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("                    EMPLOYEE DELETE CONFIRMATION");
        System.out.println("=".repeat(60));

        System.out.printf("%-20s %s%n", "ID:", r.employeeId());
        System.out.printf("%-20s %s%n", "Username:", r.username());
        System.out.printf("%-20s %s%n", "Role:", prettyEmployeeRole(r.role()));
        System.out.printf("%-20s %s%n", "Branch:", r.branch());
        System.out.printf("%-20s %s%n", "Full name:", r.fullName());
        System.out.printf("%-20s %s%n", "National ID:", r.nationalId());

        System.out.println("=".repeat(60));
    }

    // ------------------------------------------------------------------------
    // displayPasswordPolicy(): prints the current password policy values.
    // Reads from PasswordPolicy statics and formats them.
    // ------------------------------------------------------------------------
    private void displayPasswordPolicy() {
        System.out.println("\n" + "=".repeat(55));
        System.out.println("                    CURRENT PASSWORD POLICY");
        System.out.println("=".repeat(55));

        System.out.printf("%-25s %s%n", "Minimum length:", PasswordPolicy.minimumLength());
        System.out.printf("%-25s %s%n", "Require special char:", PasswordPolicy.requireSpecialChar());
        System.out.printf("%-25s %s%n", "Require letter:", PasswordPolicy.requireLetter());

        System.out.println("=".repeat(55));
    }

    // -------------------- Chat client (pretty output) --------------------

    // ------------------------------------------------------------------------
    // startChatClient(): connects to the ChatServer and drives the chat menu.
    // Sends HELLO, listens for events, and supports request/accept/join/message flow.
    // ------------------------------------------------------------------------
    private void startChatClient() {
        System.out.println("\n=== Chat ===");
        System.out.println("Connecting to chat server on port " + CHAT_PORT + " ...");

        try (Socket chat = new Socket(HOST, CHAT_PORT);
             BufferedReader chatIn = new BufferedReader(new InputStreamReader(chat.getInputStream()));
             PrintWriter chatOut = new PrintWriter(new OutputStreamWriter(chat.getOutputStream()), true)) {

            String myRole = (employeeRole == null ? "SALESPERSON" : employeeRole);
            String myBranch = (employeeBranch == null ? Branch.HOLON.name() : employeeBranch.name());
            chatOut.println("HELLO " + loggedUsername + " " + myRole + " " + myBranch);
            String hello = chatIn.readLine();
            if (hello == null || !hello.startsWith("OK HELLO")) {
                System.out.println(hello == null ? "No response" : hello);
                return;
            }

            final Map<String, String> incoming = new ConcurrentHashMap<>();
            final AtomicBoolean paired = new AtomicBoolean(false);

            Thread eventThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = chatIn.readLine()) != null) {
                        if (line.startsWith("INCOMING_REQUEST ")) {
                            String[] p = line.split(" ");
                            if (p.length >= 4) {
                                String id = p[1], fromUser = p[2], fromBranch = p[3];
                                incoming.put(id, fromUser + "@" + fromBranch);
                            }
                        } else if (line.startsWith("REQUEST_TAKEN ") || line.startsWith("REQUEST_CANCELLED ")) {
                            String[] p = line.split(" ");
                            if (p.length >= 2) incoming.remove(p[1]);
                        } else if (line.startsWith("PAIRED ")) {
                            paired.set(true);
                        } else if (line.startsWith("INFO LEFT_CONVERSATION") || line.startsWith("INFO CONVERSATION_ENDED")) {
                            paired.set(false);
                        }
                        prettyPrintChatEvent(line);
                    }
                } catch (IOException ignored) {
                }
            });
            eventThread.setDaemon(true);
            eventThread.start();

            boolean isManager = "SHIFT_MANAGER".equalsIgnoreCase(myRole);

            while (true) {
                if (paired.getAndSet(false)) {
                    System.out.println("(Paired! Entering live chat…)  Type '/end' to leave, '/quit' to disconnect)");
                    chatLoop(chatOut);
                    continue;
                }

                System.out.println("\n-- Chat Menu --");
                System.out.println("1) Request: employee by branch");
                System.out.println("2) View incoming requests (" + incoming.size() + ") and ACCEPT");
                if (isManager) {
                    System.out.println("3) List active conversations");
                    System.out.println("4) Join conversation (Shift Manager)");
                }
                System.out.println("0) Back");
                System.out.print("> ");

                String c = in.nextLine().trim();
                if ("0".equals(c)) {
                    chatOut.println("QUIT");
                    break;
                }

                if ("1".equals(c)) {
                    Branch b = askBranch();
                    paired.set(false);
                    chatOut.println("REQUEST_BRANCH " + b.name());
                    System.out.println(" Waiting for someone in " + b + " to accept…");
                    if (waitForPairing(paired, 15000)) {
                        chatLoop(chatOut);
                    } else {
                        System.out.println("No one accepted yet.");
                    }

                } else if ("2".equals(c)) {
                    if (incoming.isEmpty()) {
                        System.out.println("No incoming requests.");
                    } else {
                        System.out.println("Incoming requests:");
                        for (Map.Entry<String, String> e : incoming.entrySet()) {
                            System.out.println("  • " + e.getKey() + " from " + e.getValue());
                        }
                        System.out.print("Enter request ID to ACCEPT (blank = cancel): ");
                        String id = in.nextLine().trim();
                        if (!id.isEmpty() && incoming.containsKey(id)) {
                            paired.set(false);
                            chatOut.println("ACCEPT " + id);
                            if (waitForPairing(paired, 10000)) {
                                chatLoop(chatOut);
                            } else {
                                System.out.println("Not paired (taken/cancelled).");
                            }
                        }
                    }

                } else if (isManager && "3".equals(c)) {
                    chatOut.println("LIST_CONVS");
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignored) {
                    }

                } else if (isManager && "4".equals(c)) {
                    System.out.print("Conversation ID: ");
                    String id = in.nextLine().trim();
                    if (!id.isEmpty()) {
                        paired.set(false);
                        chatOut.println("JOIN " + id);
                        if (waitForPairing(paired, 5000)) {
                            chatLoop(chatOut);
                        } else {
                            System.out.println("Join failed or not paired.");
                        }
                    }

                } else {
                    System.out.println("Invalid choice.");
                }
            }

        } catch (IOException e) {
            System.out.println("Chat error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------
    // prettyPrintChatEvent(): renders server events in a friendly way.
    // Parses known prefixes (PAIRED, MSG, INCOMING_REQUEST, etc.) and prints messages.
    // ------------------------------------------------------------------------
    private void prettyPrintChatEvent(String line) {
        if (line == null || line.isBlank()) return;

        if (line.startsWith("PAIRED ")) {
            String[] t = line.split(" ", 3);
            String conv = (t.length > 1 ? t[1] : "?");
            String who = (t.length > 2 ? t[2].replace(",", ", ") : "?");
            System.out.println("🔗 Connected: " + who + "  (conv=" + conv + ")");
            return;
        }

        if (line.startsWith("MSG ")) {
            String rest = line.substring(4);
            int sp = rest.indexOf(' ');
            if (sp > 0) {
                String from = rest.substring(0, sp);
                String txt = rest.substring(sp + 1);
                System.out.println("💬 " + from + ": " + txt);
            } else {
                System.out.println("💬 " + rest);
            }
            return;
        }

        if (line.startsWith("INCOMING_REQUEST ")) {
            String[] p = line.split(" ");
            if (p.length >= 4) {
                System.out.println("📥 Incoming request from " + p[2] + "@" + p[3] + "  [id " + p[1] + "]");
            } else {
                System.out.println("📥 Incoming request");
            }
            return;
        }

        if (line.startsWith("REQUEST_TAKEN ")) {
            System.out.println(" Request " + line.substring("REQUEST_TAKEN ".length()) + " was taken by someone else.");
            return;
        }
        if (line.startsWith("REQUEST_CANCELLED ")) {
            System.out.println(" Request " + line.substring("REQUEST_CANCELLED ".length()) + " was cancelled.");
            return;
        }

        if (line.startsWith("MANAGER_JOINED ")) {
            System.out.println(" Manager " + line.substring("MANAGER_JOINED ".length()) + " joined the chat.");
            return;
        }

        if (line.startsWith("INFO CONVERSATION_ENDED")) {
            System.out.println(" Conversation ended.");
            return;
        }
        if (line.startsWith("INFO LEFT_CONVERSATION")) {
            System.out.println(" You left the conversation.");
            return;
        }

        if (line.startsWith("ERR ")) {
            System.out.println("❌ " + line.substring(4).replace('_', ' '));
            return;
        }

        if (line.startsWith("INFO ")) {
            System.out.println("ℹ️ " + line.substring(5).replace('_', ' '));
        } else {
            System.out.println("· " + line);
        }
    }

    // ------------------------------------------------------------------------
    // waitForPairing(): waits until the paired flag is true or timeout expires.
    // Polls every 100ms and returns the final paired state.
    // ------------------------------------------------------------------------
    private boolean waitForPairing(AtomicBoolean paired, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (!paired.get() && System.currentTimeMillis() - start < timeoutMs) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            }
        }
        return paired.get();
    }

    // ------------------------------------------------------------------------
    // chatLoop(): live chat loop after pairing.
    // Sends MSG lines, and handles '/end' and '/quit' commands to leave/exit.
    // ------------------------------------------------------------------------
    private void chatLoop(PrintWriter chatOut) {
        System.out.println("Type your messages. Use '/end' to leave the conversation, '/quit' to disconnect from chat server.");
        while (true) {
            String msg = in.nextLine();
            if (msg.equalsIgnoreCase("/end")) {
                chatOut.println("END");
                break;
            }
            if (msg.equalsIgnoreCase("/quit")) {
                chatOut.println("QUIT");
                break;
            }
            chatOut.println("MSG " + msg);
        }
    }

    // -------------------- Store actions --------------------

    // ------------------------------------------------------------------------
    // listInventory(): requests and prints inventory for a branch.
    // Reads until "OK END" and displays items in a table.
    // ------------------------------------------------------------------------
    private void listInventory(Branch branch) throws IOException {
        writer.println("LIST " + branch.name());
        String line;

        java.util.List<String> items = new java.util.ArrayList<>();
        while ((line = reader.readLine()) != null) {
            if ("OK END".equals(line)) break;
            if (line.startsWith("ITEM ")) {
                items.add(line.substring(5));
            }
        }

        if (items.isEmpty()) {
            System.out.println("No inventory items found for branch: " + branch.name());
            return;
        }

        displayInventoryTable(items, branch.name());
    }

    // ------------------------------------------------------------------------
    // displayInventoryTable(): prints a formatted table of inventory items.
    // Expects lines in the form: id,category,branch,quantity,price.
    // ------------------------------------------------------------------------
    private void displayInventoryTable(java.util.List<String> items, String branchName) {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("                    INVENTORY - " + branchName + " BRANCH");
        System.out.println("=".repeat(60));

        System.out.printf("%-8s %-15s %-10s %-12s%n",
                "ID", "Category", "Quantity", "Price");
        System.out.println("-".repeat(60));

        for (String item : items) {
            String[] parts = item.split(",");
            if (parts.length >= 5) {
                String id = parts[0];
                String category = parts[1];
                String quantity = parts[3];
                String price = parts[4];

                System.out.printf("%-8s %-15s %-10s %-12s%n",
                        id, category, quantity, price + "$");
            }
        }

        System.out.println("-".repeat(60));
        System.out.println("Total items: " + items.size());
        System.out.println("=".repeat(60) + "\n");
    }

    // ----- New: cart sale (one payment, multi-items) -----

    // ------------------------------------------------------------------------
    // sellCart(): builds a cart and sends SELL_MULTI with all items.
    // Reads the detailed receipt from server and prints a formatted summary.
    // ------------------------------------------------------------------------
    private void sellCart(Branch branch) throws IOException {
        System.out.println("\n=== New Sale (cart, one payment) ===");
        System.out.println("Enter items. Leave id blank to finish.");
        java.util.LinkedHashMap<String, Integer> cart = new java.util.LinkedHashMap<>();
        while (true) {
            System.out.print("id (blank = finish): ");
            String sku = in.nextLine().trim();
            if (sku.isEmpty()) break;
            int qty = askPositiveInt("Quantity: ");
            cart.merge(sku, qty, Integer::sum);
        }
        if (cart.isEmpty()) {
            System.out.println("Cart is empty.");
            return;
        }

        System.out.print("Customer ID: ");
        String customerId = in.nextLine().trim();

        // Encode items: sku:qty,sku:qty
        StringBuilder buldstring = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> e : cart.entrySet()) {
            if (!first) buldstring.append(",");
            buldstring.append(e.getKey()).append(":").append(e.getValue());
            first = false;
        }
        writer.println("SELL_MULTI " + branch.name() + " " + customerId + " " + buldstring);

        // Response protocol:
        // OK SALE_MULTI <type> <base> <discount> <final> [GIFT]
        // LINE <sku> <category> <qty> <unitPrice> <lineBase> <lineDiscount> <lineFinal>
        // [GIFT_SHIRT 1]
        // OK END
        String line;
        java.util.List<String> detail = new java.util.ArrayList<>();
        String header = null;
        while ((line = reader.readLine()) != null) {
            if ("OK END".equals(line)) break;
            if (line.startsWith("OK SALE_MULTI ")) header = line;
            else if (line.startsWith("ERR ")) {
                System.out.println(line.replace('_', ' '));
                return;
            } else detail.add(line);
        }
        if (header == null) {
            System.out.println("Unexpected server response.");
            return;
        }

        String[] t = header.split(" ");
        String customerType = t.length >= 3 ? t[2] : "?";
        String base = t.length >= 4 ? t[3] : "0";
        String disc = t.length >= 5 ? t[4] : "0";
        String fin = t.length >= 6 ? t[5] : "0";
        boolean gift = t.length >= 7 && "GIFT".equalsIgnoreCase(t[6]);

        System.out.println("\n" + "=".repeat(86));
        System.out.println("                               SALE RECEIPT");
        System.out.println("=".repeat(86));
        System.out.printf("%-8s %-14s %6s %10s %10s %10s %10s%n",
                "id", "Category", "Qty", "Unit", "Base", "Discount", "Line Total");
        System.out.println("-".repeat(86));
        for (String ln : detail) {
            if (ln.startsWith("LINE ")) {
                String[] p = ln.split(" ");
                if (p.length >= 8) {
                    System.out.printf("%-8s %-14s %6s %10s %10s %10s %10s%n",
                            p[1], p[2].replace('_', ' '), p[3], p[4], p[5], p[6], p[7]);
                }
            }
        }
        if (gift) {
            System.out.println("-".repeat(86));
            System.out.println("🎁 Gift: One shirt added for free (orders ≥ 300).");
        }
        System.out.println("-".repeat(86));
        System.out.printf("%-20s %s%n", "Customer type:", customerType);
        System.out.printf("%-20s %s%n", "Base total:", base);
        System.out.printf("%-20s %s%n", "Discount total:", disc);
        System.out.printf("%-20s %s%n", "Final total:", fin);
        System.out.println("=".repeat(86));
    }

    // (Legacy single-item sell; not used by the new menu but kept for compatibility)

    // ------------------------------------------------------------------------
    // orderStockToBranch(): orders quantity into branch stock for a specific SKU.
    // Sends BUY command and prints the immediate server response.
    // ------------------------------------------------------------------------
    private void orderStockToBranch(Branch branch) throws IOException {
        System.out.print("Enter ID: ");
        String sku = in.nextLine().trim();
        int quantityToAdd = askPositiveInt("Enter quantity to add: ");
        writer.println("BUY " + branch.name() + " " + sku + " " + quantityToAdd);
        String resp = reader.readLine();
        System.out.println(resp == null ? "No response" : resp);
    }

    // ------------------------------------------------------------------------
    // logout(): sends LOGOUT to server and prints the server reply.
    // Used by both admin and employee flows.
    // ------------------------------------------------------------------------
    private void logout() throws IOException {
        writer.println("LOGOUT");
        String bye = reader.readLine();
        if (bye != null) System.out.println(bye);
    }

    // -------------------- Admin Logs Menu (local view of existing log files) --------------------

    // ------------------------------------------------------------------------
    // adminLogsMenu(): small menu to view last lines from log files.
    // Lets admin select which log and prints a tail of its content.
    // ------------------------------------------------------------------------
    private void adminLogsMenu() {
        while (true) {
            System.out.println("\n-- Logs & Reports --");
            System.out.println("1) Show last 100 lines of employees log");
            System.out.println("2) Show last 100 lines of customers log");
            System.out.println("3) Show last 100 lines of transactions log");
            System.out.println("4) Show last 100 lines of auth log");
            System.out.println("5) Show last 100 lines of chat messages (CSV)");
            System.out.println("0) Back");
            System.out.print("Choice: ");
            String c = in.nextLine().trim();
            if ("0".equals(c)) return;

            String path = null;
            switch (c) {
                case "1":
                    path = "logs/employees.log";
                    break;
                case "2":
                    path = "logs/customers.log";
                    break;
                case "3":
                    path = "logs/transactions.log";
                    break;
                case "4":
                    path = "logs/auth.log";
                    break;
                case "5":
                    path = "logs/chat_messages.csv";
                    break;
                default:
                    System.out.println("Invalid choice.");
                    continue;
            }
            tailFile(path, 100);
        }
    }

    // ------------------------------------------------------------------------
    // tailFile(): prints the last n lines of a given file path.
    // Handles non-existing files gracefully with a simple message.
    // ------------------------------------------------------------------------
    private void tailFile(String filePath, int n) {
        java.nio.file.Path p = java.nio.file.Paths.get(filePath);
        if (!java.nio.file.Files.exists(p)) {
            System.out.println("(No log file yet: " + filePath + ")");
            return;
        }
        try {
            java.util.List<String> all = java.nio.file.Files.readAllLines(p);
            int from = Math.max(0, all.size() - n);
            System.out.println("\n--- " + filePath + " (last " + n + " lines) ---");
            for (int i = from; i < all.size(); i++) {
                System.out.println(all.get(i));
            }
            System.out.println("--- END ---\n");
        } catch (IOException e) {
            System.out.println("Failed to read " + filePath + ": " + e.getMessage());
        }
    }

    // -------------------- Helpers --------------------

    // ------------------------------------------------------------------------
    // askBranch(): prompts the user to pick a branch from a numeric menu.
    // Returns the chosen Branch enum constant.
    // ------------------------------------------------------------------------
    private Branch askBranch() {
        while (true) {
            System.out.println("Select branch:");
            System.out.println("1) HOLON");
            System.out.println("2) TEL_AVIV");
            System.out.println("3) RISHON");
            System.out.print("Choice: ");
            String c = in.nextLine().trim();
            switch (c) {
                case "1" -> {
                    return Branch.HOLON;
                }
                case "2" -> {
                    return Branch.TEL_AVIV;
                }
                case "3" -> {
                    return Branch.RISHON;
                }
            }
            System.out.println("Invalid choice.");
        }
    }

    // ------------------------------------------------------------------------
    // askPositiveInt(): prompts until a positive integer is entered.
    // Parses input and re-prompts on invalid values.
    // ------------------------------------------------------------------------
    private int askPositiveInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim();
            try {
                int v = Integer.parseInt(s);
                if (v > 0) return v;
            } catch (NumberFormatException ignored) {
            }
            System.out.println("Please enter a positive integer.");
        }
    }

    // ------------------------------------------------------------------------
    // askYesNo(): prompts until the user answers y/yes or n/no.
    // Returns true for yes, false for no.
    // ------------------------------------------------------------------------
    private boolean askYesNo(String prompt) {
        while (true) {
            System.out.print(prompt);
            String s = in.nextLine().trim().toLowerCase();
            if (s.equals("y") || s.equals("yes")) return true;
            if (s.equals("n") || s.equals("no")) return false;
            System.out.println("Please answer y/n.");
        }
    }

    // ------------------------------------------------------------------------
    // prettyEmployeeRole(): converts a role code to nice text.
    // ------------------------------------------------------------------------
    private String prettyEmployeeRole(String roleCode) {
        if (roleCode == null) return "Employee";
        switch (roleCode.toUpperCase()) {
            case "SHIFT_MANAGER" -> {
                return "Shift Manager";
            }
            case "CASHIER" -> {
                return "Cashier";
            }
            case "SALESPERSON" -> {
                return "Salesperson";
            }
            default -> {
                return "Employee";
            }
        }
    }
}

Clothes-Shop — Shop Network System

A multi-tier client–server application that simulates a clothing shop network with multiple branches. Employees can manage inventory and customers, process sales (including a cart/one-payment checkout), and talk with other branches using a real-time chat server. Everything is persisted to simple CSV-like text files and fully logged.

✨ Highlights

Branch-scoped inventory (per branch) + live updates for all employees at that branch

Customers with types: NEW, RETURNING, VIP

Discounts implemented by strategy classes

VIP: 12% discount; gift shirt when final cart total ≥ 300 (type logic)

Sales

Single-item sale (legacy)

Multi-item cart (SELL_MULTI) – one payment, line-level breakdown

Employees & Admin

Admin creates employees, sets password policy (min length, special char required, letter required)

Duplicate login prevention

SHA-256 password hashing

Simple account metadata (account number, phone, full name, national ID, role, branch)

Chat server

Branch-to-branch requests

First accept wins; others get “taken”

Shift Manager can list and join live conversations

Comprehensive logging

Auth, employees, customers, transactions, chat (CSV), system

Admin Logs & Reports menu to tail the latest lines

File-based storage (human-readable CSV) with starter files auto-created on first compile

Multithreaded servers (thread per client)

🗂️ Project Layout
Clothes-Shop/
├─ src/                       # All source code (client & server)
│  ├─ client/app/            # ClientConsole and menus
│  ├─ server/app/            # StoreServer main & ChatServer main
│  ├─ server/net/            # ClientHandler (store protocol)
│  ├─ server/domain/         # Domain services & models
│  │  ├─ customers/          # Customer, CustomerType, New/Returning/Vip
│  │  ├─ employees/          # Employee, EmployeeDirectory, PasswordPolicy, AuthService
│  │  ├─ invantory/          # Product, InventoryService   (typo kept to match package)
│  │  └─ sales/              # SalesService (single + multi)
│  ├─ server/shared/         # Branch enum
│  └─ server/util/           # FileDatabase, Loggers, ChatLogger
├─ data/                      # CSV-like data files (auto-created on first compile if missing)
├─ logs/                      # Log files (rotated simply by appending)
├─ out/                       # Compiled .class files
├─ compile.bat                # Compile all sources (Windows)
├─ run-server.bat             # Start StoreServer (port 5050)
├─ run-chat.bat               # Start ChatServer  (port 6060)
├─ run-client.bat             # Start ClientConsole
└─ README.md                  # This file


Ports: StoreServer 5050, ChatServer 6060, Host 127.0.0.1

🚀 Quick Start (Windows)

Compile

compile.bat


This scans src\**\*.java, compiles to out\, and ensures data\employees.txt & logs\ exist.

Run servers

run-server.bat
run-chat.bat


Run client(s)

run-client.bat


Login

Admin: username=admin, password=admin

Employees: create via Admin menu

IntelliJ: Open project, set project SDK (Java 8+), mark src as Sources Root, run mains in server.app.StoreServer, server.app.ChatServer, and client.app.ClientConsole.

🔐 Password Policy

Configurable by Admin → Set password policy:

Minimum length: default 6 (editable)

Require special char: at least one non-alphanumeric (e.g., !@#$%…)

Require letter: at least one [A–Z or a–z]

The policy applies when creating employees. Passwords are stored as SHA-256 hashes.

👥 Employees & Roles

SALESPERSON, CASHIER, SHIFT_MANAGER (for chat moderation/join)

Record format (data/employees.txt):

employeeId,username,hash,role,branch,accountNumber,phone,fullName,nationalId


Created/managed via Admin menu.

👤 Customers

Types: NEW, RETURNING, VIP (strategy classes)

VIP: 12% discount + qualifiesGiftShirt(finalTotal) → gift if finalTotal ≥ 300

Record format (data/customers.txt):

id,fullName,phone,type

📦 Inventory

Per-branch quantities and prices

Record format (data/products.txt):

sku,category,branch,quantity,price

🛒 Sales
Single-item (legacy)
SELL <branch> <sku> <qty> <customerId>
→ OK SALE <base> <discount> <final> <customerType>

Multi-item cart (one payment)
SELL_MULTI <branch> <customerId> <sku1>:<qty1>,<sku2>:<qty2>,...
→ OK SALE_MULTI <type> <base> <discount> <final> [GIFT]
   LINE <sku> <category> <qty> <unit> <lineBase> <lineDiscount> <lineFinal>
   [GIFT_SHIRT 1]
   OK END


All-or-nothing stock check

Discount strategy from customer type

Gift shirt only if type says so (VIP ≥ 300 after discount)

Example

SELL_MULTI HOLON 123456789  A1:2,B7:1

💬 Chat Server (inter-branch)

Protocol

HELLO <username> <role:SALESPERSON|CASHIER|SHIFT_MANAGER> <branch:HOLON|TEL_AVIV|RISHON>
REQUEST_BRANCH <branch>          # broadcast request to that branch
REQUEST_ANY_OTHER_BRANCH
REQUEST_USER <username>
ACCEPT <requestId>               # first accept wins
LIST_CONVS                       # (Manager) list active convs
JOIN <conversationId>            # (Manager) join
MSG <text...>
END                              # leave conversation
QUIT


Events

INCOMING_REQUEST <id> <fromUser> <fromBranch>
PAIRED <convId> <user1,user2[,manager]>
REQUEST_TAKEN <id>
REQUEST_CANCELLED <id>
MANAGER_JOINED <username>
INFO LEFT_CONVERSATION | CONVERSATION_ENDED


Client prints pretty chat lines (e.g., 💬 noa: hi).

🧾 Logs

Stored under logs\:

system.log — server/runtime events

auth.log — logins/logouts, duplicate login prevented

employees.log — add/delete employees

customers.log — add customers

transactions.log — sales (single & multi)

chat_messages.csv — conversation id, user, message text, timestamps

Admin → Logs & Reports lets you view the last N lines of each log directly from the client.

🔧 Troubleshooting

Compilation succeeded but no files
compile.bat creates out\ classes and ensures data\ + empty employees.txt. Run from project root.

“ERR LOGIN ALREADY_CONNECTED”
The same username is already connected. Logout on the other client or wait for timeout.

Password rejected
Check Admin → Password policy. Special char means any non-alphanumeric (e.g., !@#$%).

Missing product/customer
Ensure the IDs exist in data\*.txt or add via menus.

🏗️ Architecture Notes

Server-side:

StoreServer (5050) + ClientHandler (thread per connection)

ChatServer (6060) manages sessions, broadcast requests, accept-race, manager join

Domain services: single responsibility (inventory, sales, customers, employees)

CustomerType strategies encapsulate discount & gift rules

Persistence: FileDatabase does simple CSV read/write, synchronized where needed

Logging: java.util.logging wrappers in server.util.Loggers and ChatLogger

📋 Requirements

Java 8 or higher (tested with modern javac)

Windows (batch scripts). For Unix/macOS you can run mains directly from IDE or craft equivalent shell scripts.

🙌 Authors

Dana Oshri · Lihi Kimhazi · Noa Gerbi · Ron Gershtein

📣 Notes

The package invantory keeps the original name to match existing sources.

Data files are intentionally simple to keep the project portable and reviewable.

Feel free to extend: promotions, receipts to file/PDF, manager dashboards, or database backend.

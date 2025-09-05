Clothes-Shop — Shop Network System

Multi-tier client–server app for managing a clothing shop network with multiple branches. Employees handle inventory & customers, process sales (including a cart/one-payment checkout), and chat with other branches in real time. Data is stored in simple CSV-like files and fully logged.

✨ Features

Branch-scoped inventory with live updates per branch

Customers (NEW, RETURNING, VIP)

Strategy-based discounts

VIP: 12% off cart; gift shirt when final total ≥ 300

Sales

Single item sale (legacy)

Multi-item cart (SELL_MULTI) – one payment, per-line breakdown

Employees & Admin

Add/delete employees, list, set password policy (min length, special char required, letter required)

Duplicate login prevention, SHA-256 password hashing

Chat server

Inter-branch requests; first accept wins

Shift Manager can list and join active conversations

Comprehensive logging

system, auth, employees, customers, transactions, chat (CSV)

File-based storage (CSV) + auto-create starter files

Multithreaded servers (thread-per-client)

🗂️ Directory Structure
Clothes-Shop/
├─ src/
│  ├─ client/app/              # ClientConsole and menus
│  ├─ server/app/              # StoreServer main & ChatServer main
│  ├─ server/net/              # ClientHandler (store protocol)
│  ├─ server/domain/
│  │  ├─ customers/            # Customer, CustomerType, New/Returning/Vip
│  │  ├─ employees/            # Employee, EmployeeDirectory, PasswordPolicy, AuthService
│  │  ├─ invantory/            # Product, InventoryService   (name kept to match code)
│  │  └─ sales/                # SalesService (single + multi)
│  ├─ server/shared/           # Branch enum
│  └─ server/util/             # FileDatabase, Loggers, ChatLogger
├─ data/                       # CSV-like data files (auto-created if missing)
├─ logs/                       # Log files
├─ out/                        # Compiled .class files
├─ compile.bat                 # Compile all sources (Windows)
├─ run-server.bat              # Start StoreServer (5050)
├─ run-chat.bat                # Start ChatServer  (6060)
├─ run-client.bat              # Start ClientConsole
└─ README.md


Default host: 127.0.0.1. Ports: StoreServer 5050, ChatServer 6060.

🚀 Quick Start (Windows)

Compile

compile.bat


Scans src\**\*.java, compiles to out\, ensures data\ + logs\ exist, creates empty data\employees.txt on first run.

Run servers

run-server.bat
run-chat.bat


Run a client

run-client.bat


Login

Admin: username=admin, password=admin

Employees: create via Admin menu.

IntelliJ: Open project → set Project SDK (Java 8+) → mark src as Sources Root → run mains:

server.app.StoreServer

server.app.ChatServer

client.app.ClientConsole

🔐 Password Policy

Configured via Admin → Set password policy:

Minimum length (default 6)

Require special char (any non-alphanumeric, e.g. !@#$%)

Require letter (A–Z/a–z)

Passwords are hashed with SHA-256 (stored only as hash).

👥 Employees

Roles: SALESPERSON, CASHIER, SHIFT_MANAGER

Record format — data/employees.txt:

employeeId,username,hash,role,branch,accountNumber,phone,fullName,nationalId

👤 Customers

Types: NEW, RETURNING, VIP (strategy classes)

VIP rules live in VipCustomer:

applyDiscount: 12%

qualifiesGiftShirt(finalTotal): gift when final ≥ 300

Record format — data/customers.txt:

id,fullName,phone,type

📦 Inventory

Per-branch quantities & prices

Record format — data/products.txt:

sku,category,branch,quantity,price

🛒 Sales Protocol
Single item (legacy)
SELL <branch> <sku> <qty> <customerId>
→ OK SALE <base> <discount> <final> <customerType>

Multi-item cart (one payment)
SELL_MULTI <branch> <customerId> <sku1>:<qty1>,<sku2>:<qty2>,...
→ OK SALE_MULTI <type> <base> <discount> <final> [GIFT]
   LINE <sku> <category> <qty> <unit> <lineBase> <lineDiscount> <lineFinal>
   [GIFT_SHIRT 1]
   OK END


All-or-nothing stock check.

Discounts via customer type.

Gift shirt emitted only when type qualifies (VIP ≥ 300 after discount).

💬 Chat Protocol (inter-branch)
HELLO <username> <role:SALESPERSON|CASHIER|SHIFT_MANAGER> <branch:HOLON|TEL_AVIV|RISHON>
REQUEST_BRANCH <branch>
REQUEST_ANY_OTHER_BRANCH
REQUEST_USER <username>
ACCEPT <requestId>
LIST_CONVS                      # Shift Manager
JOIN <conversationId>           # Shift Manager
MSG <text...>
END
QUIT


Events

INCOMING_REQUEST <id> <fromUser> <fromBranch>
PAIRED <convId> <user1,user2[,manager]>
REQUEST_TAKEN <id> | REQUEST_CANCELLED <id>
MANAGER_JOINED <username>
INFO LEFT_CONVERSATION | CONVERSATION_ENDED


Client displays pretty chat lines (e.g., 💬 noa: hi).

🧾 Logs

Saved under logs\:

system.log — server/runtime

auth.log — logins/logouts, duplicate-login prevention

employees.log — add/delete employees

customers.log — added customers

transactions.log — sales (single & multi)

chat_messages.csv — conversation id, user, message, timestamps

Admin → Logs & Reports shows the last N lines of each log.

🛠️ Troubleshooting

Compilation ok but nothing runs → run from project root; compile.bat writes to out\ and creates data\/logs\.

“ERR LOGIN ALREADY_CONNECTED” → same username already connected; logout the other session.

Password rejected → check policy (needs special char & letter per settings).

SKU / customer not found → add via menus or edit files in data\.

📐 Architecture

StoreServer (5050): ClientHandler per connection

ChatServer (6060): sessions, broadcast requests, accept-race, manager join

Domain services: InventoryService, SalesService, CustomerService, EmployeeDirectory

CustomerType strategies encapsulate discounts & gifts

Persistence: FileDatabase (CSV read/write)

Logging: server.util.Loggers + ChatLogger

✅ Requirements

Java 8+

Windows (batch scripts) — or run from IDE

👩‍💻 Authors

Dana Oshri · Lihi Kimhazi · Noa Gerbi · Ron Gershtein

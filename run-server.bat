@echo off
cd /d "%~dp0"
echo [Server] starting on 5050...
java -cp out server.app.StoreServer 5050

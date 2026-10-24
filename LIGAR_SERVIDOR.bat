@echo off
title Servidor de Mensagens - Central
color 0A
echo ============================================
echo   LIGANDO O SERVIDOR DO WHATSAPP JAVA...
echo ============================================
cd /d "%~dp0"

java -cp "target/classes;%USERPROFILE%/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar" server.Servidor

pause
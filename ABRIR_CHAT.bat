@echo off
cd /d "%~dp0"
start javaw -cp "target/classes;%USERPROFILE%/.m2/repository/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar" client.ClienteChat
exit
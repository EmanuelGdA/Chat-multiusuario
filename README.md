# 💬 ConnectChat - Sistema de Mensagens Multiusuário

Sistema de mensagens instantâneas desktop desenvolvido em Java para a disciplina de **Programação Orientada a Objetos**.  
Implementa comunicação em tempo real via **Sockets TCP**, processamento concorrente com **Multithreading**, interface gráfica nativa em **Java Swing** com fluxo em duas colunas e persistência de histórico via **JDBC com MySQL**.

---

## 🛠️ Tecnologias
- **Linguagem:** Java (JDK 17+)
- **Persistência:** MySQL 8.x via JDBC (`mysql-connector-j`)
- **Interface Gráfica:** Java Swing (Interface moderna baseada em componentes desacoplados)
- **Concorrência:** Java Threads e Coleções Sincronizadas
- **Testes Automatizados:** JUnit 5

---

## 📁 Arquitetura em Pacotes
- `client`: Interface gráfica desktop do usuário (`ClienteChat`).
- `server`: Núcleo de rede socket e atendimento concorrente (`Servidor`, `ClienteHandler`).
- `model`: Entidades de domínio e regras de negócio (`Usuario`, `Mensagem`).
- `persistence`: Camada de acesso ao banco isolada por Repository (`MensagemRepository`, `ConexaoFactory`).

---

## 🚀 Como Executar

### 1. Criar o Banco no MySQL
No MySQL Workbench, execute o script:
```sql
CREATE DATABASE IF NOT EXISTS chat_db;
USE chat_db;

CREATE TABLE IF NOT EXISTS mensagens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    remetente VARCHAR(100) NOT NULL,
    conteudo TEXT NOT NULL,
    horario DATETIME NOT NULL
);
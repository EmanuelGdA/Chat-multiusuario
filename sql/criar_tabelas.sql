CREATE DATABASE IF NOT EXISTS chat_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE chat_db;

CREATE TABLE IF NOT EXISTS mensagens (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    remetente VARCHAR(100) NOT NULL,
    conteudo TEXT NOT NULL,
    horario DATETIME NOT NULL
);

CREATE TABLE IF NOT EXISTS mensagens_privadas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    chave_conversa CHAR(64) NOT NULL,
    remetente_id VARCHAR(120) NOT NULL,
    remetente VARCHAR(120) NOT NULL,
    destinatario_id VARCHAR(120) NOT NULL,
    conteudo TEXT NOT NULL,
    horario DATETIME NOT NULL,
    INDEX idx_privadas_conversa_data (chave_conversa, horario, id),
    INDEX idx_privadas_remetente (remetente_id),
    INDEX idx_privadas_destinatario (destinatario_id)
);

CREATE TABLE IF NOT EXISTS arquivos_chat (
    arquivo_id CHAR(36) PRIMARY KEY,
    chave_conversa CHAR(64) NOT NULL,
    remetente_id VARCHAR(120) NOT NULL,
    remetente VARCHAR(120) NOT NULL,
    destinatario_id VARCHAR(120) NOT NULL,
    nome VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(150) NOT NULL,
    conteudo MEDIUMBLOB NOT NULL,
    horario DATETIME NOT NULL,
    INDEX idx_arquivos_chat_conversa_data (chave_conversa, horario, arquivo_id),
    INDEX idx_arquivos_chat_remetente (remetente_id),
    INDEX idx_arquivos_chat_destinatario (destinatario_id)
);

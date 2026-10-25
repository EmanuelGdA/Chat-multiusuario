package model;

import java.time.LocalDateTime;
import java.util.UUID;

/** Arquivo compartilhado em uma conversa privada. */
public final class ArquivoPrivado {
    private final String id;
    private final String remetenteId;
    private final String remetenteNome;
    private final String destinatarioId;
    private final String nome;
    private final String tipoMime;
    private final byte[] conteudo;
    private final LocalDateTime horario;

    public ArquivoPrivado(String id, String remetenteId, String remetenteNome,
                          String destinatarioId, String nome, String tipoMime,
                          byte[] conteudo, LocalDateTime horario) {
        this.id = exigir(id, "Identificador do arquivo");
        UUID.fromString(this.id);
        this.remetenteId = exigir(remetenteId, "Remetente");
        this.remetenteNome = exigir(remetenteNome, "Nome do remetente");
        this.destinatarioId = exigir(destinatarioId, "Destinatário");
        this.nome = exigir(nome, "Nome do arquivo");
        this.tipoMime = tipoMime == null || tipoMime.isBlank()
                ? "application/octet-stream" : tipoMime.trim();
        if (conteudo == null || conteudo.length == 0) {
            throw new IllegalArgumentException("O arquivo está vazio.");
        }
        this.conteudo = conteudo.clone();
        this.horario = horario == null ? LocalDateTime.now() : horario;
    }

    private static String exigir(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ficar vazio.");
        }
        return valor.trim();
    }

    public String getId() { return id; }
    public String getRemetenteId() { return remetenteId; }
    public String getRemetenteNome() { return remetenteNome; }
    public String getDestinatarioId() { return destinatarioId; }
    public String getNome() { return nome; }
    public String getTipoMime() { return tipoMime; }
    public byte[] getConteudo() { return conteudo.clone(); }
    public LocalDateTime getHorario() { return horario; }
}

package model;

import java.time.LocalDateTime;

public class MensagemPrivada {
    private final String remetenteId;
    private final String remetenteNome;
    private final String destinatarioId;
    private final String conteudo;
    private final LocalDateTime horario;

    public MensagemPrivada(String remetenteId, String remetenteNome, String destinatarioId,
                           String conteudo, LocalDateTime horario) {
        this.remetenteId = exigirTexto(remetenteId, "Remetente");
        this.remetenteNome = exigirTexto(remetenteNome, "Nome do remetente");
        this.destinatarioId = exigirTexto(destinatarioId, "Destinatário");
        this.conteudo = exigirTexto(conteudo, "Conteúdo");
        this.horario = horario == null ? LocalDateTime.now() : horario;
    }

    private static String exigirTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException(campo + " não pode ficar vazio.");
        }
        return valor.trim();
    }

    public String getRemetenteId() { return remetenteId; }
    public String getRemetenteNome() { return remetenteNome; }
    public String getDestinatarioId() { return destinatarioId; }
    public String getConteudo() { return conteudo; }
    public LocalDateTime getHorario() { return horario; }
}
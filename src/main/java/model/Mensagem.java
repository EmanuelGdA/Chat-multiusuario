package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Mensagem {
    private Long id;
    private String remetente;
    private String conteudo;
    private LocalDateTime horario;

    // Padrao para formatar a hora como [14:30:15]
    private static final DateTimeFormatter FORMATADOR = DateTimeFormatter.ofPattern("HH:mm:ss");

    // Construtor 1: Usado ao buscar mensagens que ja existem no banco de dados (ja possuem id e horario)
    public Mensagem(Long id, String remetente, String conteudo, LocalDateTime horario) {
        this(remetente, conteudo);
        this.id = id;
        this.horario = (horario != null) ? horario : LocalDateTime.now();
    }

    // Construtor 2: Usado para novas mensagens digitadas no chat (gera o horario atual)
    public Mensagem(String remetente, String conteudo) {
        if (remetente == null || remetente.trim().isEmpty()) {
            throw new IllegalArgumentException("Remetente nao pode ser vazio.");
        }
        if (conteudo == null || conteudo.trim().isEmpty()) {
            throw new IllegalArgumentException("Conteudo da mensagem nao pode ser vazio.");
        }
        this.remetente = remetente.trim();
        this.conteudo = conteudo.trim();
        this.horario = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRemetente() {
        return remetente;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getHorario() {
        return horario;
    }

    // Formata a mensagem para ser exibida na tela do usuario
    public String formatarParaExibicao() {
        return String.format("[%s] %s: %s", horario.format(FORMATADOR), remetente, conteudo);
    }
}
package model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

public class MensagemTest {

    // Teste 1: Garante que uma mensagem valida e criada com remetente, conteudo e horario
    @Test
    @DisplayName("Deve instanciar uma mensagem valida corretamente")
    void deveCriarMensagemValida() {
        Mensagem msg = new Mensagem("Carlos", "Ola mundo!");

        assertEquals("Carlos", msg.getRemetente());
        assertEquals("Ola mundo!", msg.getConteudo());
        assertNotNull(msg.getHorario());
    }

    // Teste 2: Garante a formatacao [HH:mm:ss] Remetente: Conteudo
    @Test
    @DisplayName("Deve formatar a mensagem para exibicao no chat corretamente")
    void deveFormatarMensagemParaExibicao() {
        LocalDateTime dataFixa = LocalDateTime.of(2026, 10, 20, 15, 30, 0);
        Mensagem msg = new Mensagem(1L, "Carlos", "Mensagem de teste", dataFixa);

        String textoFormatado = msg.formatarParaExibicao();

        assertEquals("[15:30:00] Carlos: Mensagem de teste", textoFormatado);
    }

    // Teste 3: Regra de negocio que impede mensagem com remetente em branco
    @Test
    @DisplayName("Deve barrar criacao de mensagem com remetente vazio")
    void deveLancarExcecaoQuandoRemetenteVazio() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Mensagem("   ", "Texto qualquer");
        });
    }

    // Teste 4: Regra de negocio que impede envio de texto vazio
    @Test
    @DisplayName("Deve barrar criacao de mensagem com conteudo vazio")
    void deveLancarExcecaoQuandoConteudoVazio() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Mensagem("Carlos", "    ");
        });
    }

    // Teste 5: Testa a classe Usuario para garantir que o apelido e validado
    @Test
    @DisplayName("Deve barrar criacao de usuario com apelido vazio")
    void deveLancarExcecaoQuandoApelidoUsuarioInvalido() {
        assertThrows(IllegalArgumentException.class, () -> {
            new Usuario("");
        });
    }
}
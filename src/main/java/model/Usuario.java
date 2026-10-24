package model;

import java.util.Objects;

public class Usuario {
    private String apelido;

    public Usuario(String apelido) {
        setApelido(apelido);
    }

    public String getApelido() {
        return apelido;
    }

    public void setApelido(String apelido) {
        // Regra de negocio: nao permite usuario sem nome ou so com espacos
        if (apelido == null || apelido.trim().isEmpty()) {
            throw new IllegalArgumentException("O apelido nao pode ser vazio.");
        }
        this.apelido = apelido.trim();
    }

    // Usado para comparar se dois usuarios sao iguais pelo apelido
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return Objects.equals(apelido, usuario.apelido);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apelido);
    }

    @Override
    public String toString() {
        return apelido;
    }
}
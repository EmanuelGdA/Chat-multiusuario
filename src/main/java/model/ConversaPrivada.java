package model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public final class ConversaPrivada {
    private ConversaPrivada() { }

    public static String gerarChave(String idA, String idB) {
        String primeiro = normalizar(idA);
        String segundo = normalizar(idB);
        String par = primeiro.compareTo(segundo) <= 0
                ? primeiro + "\u0000" + segundo
                : segundo + "\u0000" + primeiro;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(par.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte valor : hash) {
                hex.append(String.format("%02x", valor));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível.", e);
        }
    }

    private static String normalizar(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Identificador não pode ficar vazio.");
        }
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
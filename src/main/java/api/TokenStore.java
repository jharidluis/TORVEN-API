package api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import modelos.Usuario;

/**
 * Sesiones en memoria para la API movil. Sencillo a proposito: son pocos
 * usuarios (unos 5 vendedores), no hace falta una base de sesiones aparte.
 * Si el servidor se reinicia, todos vuelven a iniciar sesion.
 */
public final class TokenStore {
    private static final long DURACION_MS = 12L * 60 * 60 * 1000; // 12 horas

    private static final class Sesion {
        final Usuario usuario;
        final long expiraEn;

        Sesion(Usuario usuario, long expiraEn) {
            this.usuario = usuario;
            this.expiraEn = expiraEn;
        }
    }

    private final Map<String, Sesion> sesiones = new ConcurrentHashMap<String, Sesion>();

    public String crear(Usuario usuario) {
        String token = UUID.randomUUID().toString();
        sesiones.put(token, new Sesion(usuario, System.currentTimeMillis() + DURACION_MS));
        return token;
    }

    public Usuario validar(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        Sesion sesion = sesiones.get(token);
        if (sesion == null) {
            return null;
        }
        if (sesion.expiraEn < System.currentTimeMillis()) {
            sesiones.remove(token);
            return null;
        }
        return sesion.usuario;
    }

    public void invalidar(String token) {
        if (token != null) {
            sesiones.remove(token);
        }
    }

    public String extraerToken(String encabezadoAuthorization) {
        if (encabezadoAuthorization == null) {
            return null;
        }
        String prefijo = "Bearer ";
        if (encabezadoAuthorization.startsWith(prefijo)) {
            return encabezadoAuthorization.substring(prefijo.length()).trim();
        }
        return null;
    }
}

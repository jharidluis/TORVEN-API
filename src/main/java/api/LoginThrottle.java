package api;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Frena intentos repetidos de login por IP para dificultar ataques de fuerza
 * bruta contra la API movil. En memoria a proposito: son pocos vendedores,
 * no hace falta infraestructura aparte, y un reinicio del servidor limpia
 * cualquier bloqueo colgado.
 */
public final class LoginThrottle {
    private static final int INTENTOS_MAXIMOS = 5;
    private static final long BLOQUEO_MS = 5L * 60 * 1000; // 5 minutos

    private static final class Intentos {
        final AtomicInteger fallos = new AtomicInteger(0);
        volatile long bloqueadoHasta = 0;
    }

    private final ConcurrentHashMap<String, Intentos> porIp = new ConcurrentHashMap<String, Intentos>();

    public boolean bloqueado(String ip) {
        Intentos intentos = porIp.get(clave(ip));
        if (intentos == null) {
            return false;
        }
        if (intentos.bloqueadoHasta != 0 && intentos.bloqueadoHasta < System.currentTimeMillis()) {
            porIp.remove(clave(ip));
            return false;
        }
        return intentos.bloqueadoHasta != 0;
    }

    public void registrarFallo(String ip) {
        Intentos intentos = porIp.computeIfAbsent(clave(ip), k -> new Intentos());
        int fallos = intentos.fallos.incrementAndGet();
        if (fallos >= INTENTOS_MAXIMOS) {
            intentos.bloqueadoHasta = System.currentTimeMillis() + BLOQUEO_MS;
        }
    }

    public void registrarExito(String ip) {
        porIp.remove(clave(ip));
    }

    private String clave(String ip) {
        return ip == null || ip.trim().isEmpty() ? "desconocida" : ip.trim();
    }
}

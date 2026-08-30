package modelos;

public final class VentaEstado {
    public static final String EN_PROCESO = "EN_PROCESO";
    public static final String VENDIDA = "VENDIDA";
    public static final String CANCELADA = "CANCELADA";

    private VentaEstado() {
    }

    public static String normalizar(String estado) {
        String limpio = estado == null ? "" : estado.trim().toUpperCase();
        if ("PAGADA".equals(limpio)) {
            return VENDIDA;
        }
        if (VENDIDA.equals(limpio) || CANCELADA.equals(limpio)) {
            return limpio;
        }
        return EN_PROCESO;
    }

    public static String etiqueta(String estado) {
        String normalizado = normalizar(estado);
        if (VENDIDA.equals(normalizado)) {
            return "Vendida";
        }
        if (CANCELADA.equals(normalizado)) {
            return "Cancelada";
        }
        return "En proceso";
    }
}

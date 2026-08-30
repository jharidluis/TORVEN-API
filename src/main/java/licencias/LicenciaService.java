package licencias;

import configuracion.Conexion;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/** Consulta el estado manual de la licencia almacenada en Railway. */
public final class LicenciaService {
    private static final int LICENCIA_PRINCIPAL = 1;
    private static final String MENSAJE_PREDETERMINADO =
            "El acceso a Torven está suspendido. Comunícate con el proveedor para reactivar la licencia.";

    private LicenciaService() {
    }

    public static Resultado verificar() throws SQLException {
        String sql = "SELECT estado, mensaje_bloqueo FROM licencia WHERE id_licencia = ?";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, LICENCIA_PRINCIPAL);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No existe la licencia principal de Torven.");
                }

                String estado = texto(rs.getString("estado")).toUpperCase(Locale.ROOT);
                if ("ACTIVA".equals(estado)) {
                    return new Resultado(true, "");
                }
                if ("SUSPENDIDA".equals(estado)) {
                    String mensaje = texto(rs.getString("mensaje_bloqueo"));
                    return new Resultado(false, mensaje.isEmpty() ? MENSAJE_PREDETERMINADO : mensaje);
                }
                throw new SQLException("La licencia tiene un estado no reconocido: " + estado);
            }
        }
    }

    private static String texto(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Resultado {
        private final boolean accesoPermitido;
        private final String mensaje;

        private Resultado(boolean accesoPermitido, String mensaje) {
            this.accesoPermitido = accesoPermitido;
            this.mensaje = mensaje;
        }

        public boolean isAccesoPermitido() {
            return accesoPermitido;
        }

        public String getMensaje() {
            return mensaje;
        }
    }
}

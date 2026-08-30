package configuracion;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SqlIds {
    // Si una tabla usa AUTO_INCREMENT no cambia en tiempo de ejecucion, asi que se
    // consulta una sola vez y se reutiliza. Antes se pedian los metadatos a la base
    // de datos en cada insercion, lo que sumaba un viaje de red extra por cada venta,
    // cliente o producto guardado.
    private static final Map<String, Boolean> CACHE_ID_MANUAL = new ConcurrentHashMap<String, Boolean>();

    private SqlIds() {
    }

    public static boolean requiereIdManual(Connection conn, String tabla, String columna) throws SQLException {
        String clave = tabla + "." + columna;
        Boolean cacheado = CACHE_ID_MANUAL.get(clave);
        if (cacheado != null) {
            return cacheado.booleanValue();
        }
        boolean resultado = consultarIdManual(conn, tabla, columna);
        CACHE_ID_MANUAL.put(clave, Boolean.valueOf(resultado));
        return resultado;
    }

    private static boolean consultarIdManual(Connection conn, String tabla, String columna) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(catalogo(conn), null, tabla, columna)) {
            if (!rs.next()) {
                return false;
            }
            String autoIncrement = rs.getString("IS_AUTOINCREMENT");
            return !"YES".equalsIgnoreCase(autoIncrement);
        }
    }

    public static int siguienteInt(Connection conn, String tabla, String columna) throws SQLException {
        long siguiente = siguienteLong(conn, tabla, columna);
        if (siguiente > Integer.MAX_VALUE) {
            throw new SQLException("El contador de " + tabla + "." + columna + " excede el limite permitido.");
        }
        return (int) siguiente;
    }

    public static long siguienteLong(Connection conn, String tabla, String columna) throws SQLException {
        String sql = "SELECT COALESCE(MAX(" + sqlNombre(columna) + "), 0) + 1 FROM " + sqlNombre(tabla);
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        }
        return 1L;
    }

    private static String catalogo(Connection conn) throws SQLException {
        String catalogo = conn.getCatalog();
        return catalogo == null || catalogo.trim().isEmpty() ? AppConfig.get().databaseName() : catalogo;
    }

    private static String sqlNombre(String nombre) {
        return "`" + nombre.replace("`", "``") + "`";
    }
}

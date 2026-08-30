package configuracion;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public final class Conexion {
    private static HikariDataSource pool;

    private Conexion() {
    }

    public static Connection abrir() throws SQLException {
        cargarDriver();
        Connection conn = obtenerPool().getConnection();
        try {
            prepararAuditoria(conn);
            return conn;
        } catch (SQLException ex) {
            conn.close();
            throw ex;
        }
    }

    public static Connection abrirServidor() throws SQLException {
        cargarDriver();
        AppConfig config = AppConfig.get();
        return DriverManager.getConnection(config.jdbcServerUrl(), config.user(), config.password());
    }

    private static void cargarDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException("Falta MySQL Connector/J. Abre el proyecto con Maven o agrega el jar del conector.", ex);
        }
    }

    private static synchronized HikariDataSource obtenerPool() {
        if (pool != null) {
            return pool;
        }

        AppConfig config = AppConfig.get();
        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("TorvenPool");
        hikari.setJdbcUrl(config.jdbcDatabaseUrl());
        hikari.setUsername(config.user());
        hikari.setPassword(config.password());
        // minimumIdle en 4 mantiene conexiones ya abiertas listas para ~5 usuarios
        // simultaneos; con 1 sola, cada usuario extra tenia que esperar a que se
        // abriera una conexion nueva contra el proxy remoto de Railway.
        hikari.setMinimumIdle(4);
        hikari.setMaximumPoolSize(10);
        hikari.setConnectionTimeout(10000L);
        hikari.setValidationTimeout(5000L);
        hikari.setIdleTimeout(300000L);
        hikari.setMaxLifetime(1200000L);
        // Evita que el proxy de Railway cierre conexiones inactivas en silencio;
        // sin esto, la primera consulta despues de un rato sin uso se sentia lenta
        // o fallaba porque la conexion ya estaba muerta.
        hikari.setKeepaliveTime(240000L);
        hikari.addDataSourceProperty("cachePrepStmts", "true");
        hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        hikari.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikari.addDataSourceProperty("useServerPrepStmts", "true");
        pool = new HikariDataSource(hikari);
        return pool;
    }

    private static void prepararAuditoria(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("SET @app_usuario_id = ?, @app_usuario = ?")) {
            Integer usuarioId = AuditoriaContext.usuarioId();
            if (usuarioId == null) {
                ps.setNull(1, java.sql.Types.INTEGER);
                ps.setNull(2, java.sql.Types.VARCHAR);
            } else {
                ps.setInt(1, usuarioId.intValue());
                ps.setString(2, AuditoriaContext.usuario());
            }
            ps.execute();
        }
    }
}

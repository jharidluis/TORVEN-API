package dao;

import configuracion.Conexion;
import configuracion.SqlIds;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import modelos.CategoriaGasto;
import modelos.Gasto;
import modelos.ResumenMensualGastos;
import modelos.TotalCategoriaGasto;

public class GastoDAO {
    private static final String SELECT_BASE =
            "SELECT g.id_gasto, g.id_categoria_gasto, cg.nombre AS categoria, g.descripcion, g.monto, "
            + "g.id_usuario, u.nombre AS usuario, g.creado_en "
            + "FROM gasto g "
            + "INNER JOIN categoria_gasto cg ON cg.id_categoria_gasto = g.id_categoria_gasto "
            + "LEFT JOIN usuario u ON u.id_usuario = g.id_usuario ";

    public List<CategoriaGasto> listarCategorias() throws SQLException {
        List<CategoriaGasto> categorias = new ArrayList<CategoriaGasto>();
        String sql = "SELECT id_categoria_gasto, nombre FROM categoria_gasto "
                + "ORDER BY CASE WHEN nombre = 'Otros' THEN 1 ELSE 0 END, nombre";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categorias.add(new CategoriaGasto(
                        rs.getInt("id_categoria_gasto"),
                        rs.getString("nombre")));
            }
        }
        return categorias;
    }

    public Gasto obtenerPorId(int id) throws SQLException {
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(SELECT_BASE + "WHERE g.id_gasto = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public List<Gasto> listarHoy() throws SQLException {
        // "g.creado_en >= hoy AND < manana" en vez de "DATE(g.creado_en) = CURDATE()":
        // envolver la columna en una funcion le impide a MySQL usar el indice
        // idx_gasto_creado_en (forzaria un escaneo completo de la tabla). Con
        // el rango sargable, esto sigue siendo rapido aunque la tabla crezca
        // a miles de gastos historicos.
        List<Gasto> gastos = new ArrayList<Gasto>();
        String sql = SELECT_BASE
                + "WHERE g.creado_en >= CURDATE() AND g.creado_en < CURDATE() + INTERVAL 1 DAY "
                + "ORDER BY g.creado_en DESC";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                gastos.add(mapear(rs));
            }
        }
        return gastos;
    }

    public ResumenMensualGastos resumenMensual() throws SQLException {
        List<TotalCategoriaGasto> porCategoria = new ArrayList<TotalCategoriaGasto>();
        BigDecimal total = BigDecimal.ZERO;
        // Mismo motivo que en listarHoy(): rango sargable sobre g.creado_en
        // (no YEAR()/MONTH() envolviendo la columna) para poder usar el
        // indice idx_gasto_creado_en sin importar cuantos gastos se acumulen.
        String sql = "SELECT cg.nombre AS categoria, COALESCE(SUM(g.monto), 0) AS total "
                + "FROM categoria_gasto cg "
                + "LEFT JOIN gasto g ON g.id_categoria_gasto = cg.id_categoria_gasto "
                + "  AND g.creado_en >= DATE_FORMAT(CURDATE(), '%Y-%m-01') "
                + "  AND g.creado_en < DATE_FORMAT(CURDATE(), '%Y-%m-01') + INTERVAL 1 MONTH "
                + "GROUP BY cg.id_categoria_gasto, cg.nombre "
                + "ORDER BY CASE WHEN cg.nombre = 'Otros' THEN 1 ELSE 0 END, cg.nombre";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                BigDecimal subtotal = rs.getBigDecimal("total");
                porCategoria.add(new TotalCategoriaGasto(rs.getString("categoria"), subtotal));
                total = total.add(subtotal);
            }
        }
        return new ResumenMensualGastos(total, porCategoria);
    }

    public void crear(Gasto gasto) throws SQLException {
        validar(gasto);
        try (Connection conn = Conexion.abrir()) {
            if (SqlIds.requiereIdManual(conn, "gasto", "id_gasto")) {
                insertarConId(conn, gasto);
                return;
            }

            String sql = "INSERT INTO gasto(id_categoria_gasto, descripcion, monto, id_usuario) "
                    + "VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, gasto.getIdCategoriaGasto());
                ps.setString(2, gasto.getDescripcion());
                ps.setBigDecimal(3, gasto.getMonto());
                if (gasto.getIdUsuario() == null) {
                    ps.setNull(4, java.sql.Types.INTEGER);
                } else {
                    ps.setInt(4, gasto.getIdUsuario());
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        gasto.setId(keys.getInt(1));
                    }
                }
            }
        }
    }

    private void insertarConId(Connection conn, Gasto gasto) throws SQLException {
        int id = SqlIds.siguienteInt(conn, "gasto", "id_gasto");
        String sql = "INSERT INTO gasto(id_gasto, id_categoria_gasto, descripcion, monto, id_usuario) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setInt(2, gasto.getIdCategoriaGasto());
            ps.setString(3, gasto.getDescripcion());
            ps.setBigDecimal(4, gasto.getMonto());
            if (gasto.getIdUsuario() == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, gasto.getIdUsuario());
            }
            ps.executeUpdate();
            gasto.setId(id);
        }
    }

    private Gasto mapear(ResultSet rs) throws SQLException {
        int idUsuario = rs.getInt("id_usuario");
        Timestamp creadoEn = rs.getTimestamp("creado_en");
        return new Gasto(
                rs.getInt("id_gasto"),
                rs.getInt("id_categoria_gasto"),
                rs.getString("categoria"),
                rs.getString("descripcion"),
                rs.getBigDecimal("monto"),
                rs.wasNull() ? null : Integer.valueOf(idUsuario),
                rs.getString("usuario"),
                creadoEn == null ? null : creadoEn.toLocalDateTime());
    }

    private void validar(Gasto gasto) throws SQLException {
        if (gasto.getIdCategoriaGasto() <= 0) {
            throw new SQLException("Selecciona una categoria de gasto.");
        }
        if (gasto.getDescripcion() == null || gasto.getDescripcion().trim().isEmpty()) {
            throw new SQLException("Ingresa una descripcion del gasto.");
        }
        gasto.setDescripcion(gasto.getDescripcion().trim());
        if (gasto.getDescripcion().length() > 200) {
            throw new SQLException("La descripcion es demasiado larga (maximo 200 caracteres).");
        }
        if (gasto.getMonto() == null || gasto.getMonto().compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("Ingresa un monto valido.");
        }
    }
}

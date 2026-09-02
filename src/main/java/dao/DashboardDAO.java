package dao;

import configuracion.Conexion;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import modelos.DashboardDatos;
import modelos.LineaVenta;
import modelos.LugarEntrega;
import modelos.VentaEstado;
import modelos.VentaTicket;

public class DashboardDAO {
    public DashboardDatos cargar(LocalDate desde, LocalDate hasta, boolean canceladas) throws SQLException {
        try (Connection conn = Conexion.abrir();
             CallableStatement call = conn.prepareCall("{CALL sp_dashboard_cargar(?, ?, ?)}")) {
            LocalDate fechaDesde = desde == null ? LocalDate.now() : desde;
            LocalDate fechaHasta = hasta == null ? fechaDesde : hasta;
            call.setDate(1, java.sql.Date.valueOf(fechaDesde));
            call.setDate(2, java.sql.Date.valueOf(fechaHasta));
            call.setInt(3, canceladas ? 1 : 0);

            if (!call.execute()) {
                throw new SQLException("El procedimiento del Dashboard no devolvio el resumen.");
            }

            BigDecimal hoy;
            BigDecimal semana;
            BigDecimal mes;
            int lugaresEntrega;
            int productos;
            int stockBajo;
            try (ResultSet rs = call.getResultSet()) {
                if (!rs.next()) {
                    throw new SQLException("El procedimiento del Dashboard devolvio un resumen vacio.");
                }
                hoy = rs.getBigDecimal("ventas_hoy");
                semana = rs.getBigDecimal("ventas_semana");
                mes = rs.getBigDecimal("ventas_mes");
                lugaresEntrega = rs.getInt("total_lugares_entrega");
                productos = rs.getInt("total_productos");
                stockBajo = rs.getInt("stock_bajo");
            }

            if (!call.getMoreResults(Statement.CLOSE_CURRENT_RESULT)) {
                throw new SQLException("El procedimiento del Dashboard no devolvio los distritos.");
            }
            List<Object[]> distritos;
            try (ResultSet rs = call.getResultSet()) {
                distritos = leerDistritos(rs);
            }

            if (!call.getMoreResults(Statement.CLOSE_CURRENT_RESULT)) {
                throw new SQLException("El procedimiento del Dashboard no devolvio las ventas.");
            }
            List<Object[]> ventas;
            try (ResultSet rs = call.getResultSet()) {
                ventas = leerVentas(rs);
            }
            return new DashboardDatos(hoy, semana, mes, lugaresEntrega, productos, stockBajo, distritos, ventas);
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1305) {
                throw ex;
            }
            return cargarSinProcedimiento(desde, hasta, canceladas);
        }
    }

    public BigDecimal ventasHoy() throws SQLException {
        LocalDate hoy = LocalDate.now();
        return ventasPorPeriodo(hoy, hoy.plusDays(1));
    }

    public BigDecimal ventasSemana() throws SQLException {
        LocalDate inicioSemana = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ventasPorPeriodo(inicioSemana, inicioSemana.plusWeeks(1));
    }

    public BigDecimal ventasMes() throws SQLException {
        LocalDate inicioMes = LocalDate.now().withDayOfMonth(1);
        return ventasPorPeriodo(inicioMes, inicioMes.plusMonths(1));
    }

    public int totalLugaresEntrega() throws SQLException {
        return entero("SELECT COUNT(*) FROM lugar_entrega");
    }

    public int totalProductos() throws SQLException {
        return entero("SELECT COUNT(*) FROM producto WHERE activo = 1");
    }

    public int productosStockBajo() throws SQLException {
        return entero("SELECT COUNT(*) FROM producto WHERE activo = 1 AND stock <= 5");
    }

    public List<Object[]> ventasPorRango(LocalDate desde, LocalDate hasta) throws SQLException {
        return ventasPorRango(desde, hasta, false);
    }

    public List<Object[]> ventasPorRango(LocalDate desde, LocalDate hasta, boolean canceladas) throws SQLException {
        LocalDate fechaDesde = desde == null ? LocalDate.now() : desde;
        LocalDate fechaHasta = hasta == null ? fechaDesde : hasta;
        List<Object[]> ventas = new ArrayList<Object[]>();
        String sql = "SELECT v.id_venta, v.fecha_venta, le.direccion, "
                + "COALESCE(v.documento_comprobante, '') AS dni_ruc, "
                + "v.total, v.estado "
                + "FROM venta v INNER JOIN lugar_entrega le ON le.id_lugar_entrega = v.id_lugar_entrega "
                + "WHERE v.fecha_venta >= ? AND v.fecha_venta < ? "
                + (canceladas ? "AND v.estado = ? " : "AND v.estado IN ('VENDIDA', 'PAGADA') ")
                + "ORDER BY v.fecha_venta DESC";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(fechaDesde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(fechaHasta.plusDays(1).atStartOfDay()));
            if (canceladas) {
                ps.setString(3, VentaEstado.CANCELADA);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ventas.add(new Object[]{
                        rs.getLong("id_venta"),
                        rs.getTimestamp("fecha_venta"),
                        rs.getString("direccion"),
                        rs.getString("dni_ruc"),
                        rs.getBigDecimal("total"),
                        VentaEstado.normalizar(rs.getString("estado"))
                    });
                }
            }
        }
        return ventas;
    }

    public List<Object[]> ventasPorDistrito() throws SQLException {
        List<Object[]> distritos = new ArrayList<Object[]>();
        String sql = "SELECT COALESCE(d.nombre, 'Otro') AS distrito, "
                + "COALESCE(SUM(v.total), 0) AS total, COUNT(*) AS ventas "
                + "FROM venta v "
                + "INNER JOIN lugar_entrega le ON le.id_lugar_entrega = v.id_lugar_entrega "
                + "LEFT JOIN distritos d ON d.id_distrito = le.id_distrito "
                + "WHERE v.estado IN ('VENDIDA', 'PAGADA') "
                + "GROUP BY COALESCE(d.nombre, 'Otro') "
                + "ORDER BY total DESC, ventas DESC, distrito ASC LIMIT 8";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                distritos.add(new Object[]{
                    rs.getString("distrito"),
                    rs.getBigDecimal("total"),
                    Integer.valueOf(rs.getInt("ventas"))
                });
            }
        }
        return distritos;
    }

    private DashboardDatos cargarSinProcedimiento(LocalDate desde, LocalDate hasta, boolean canceladas)
            throws SQLException {
        return new DashboardDatos(
                ventasHoy(),
                ventasSemana(),
                ventasMes(),
                totalLugaresEntrega(),
                totalProductos(),
                productosStockBajo(),
                ventasPorDistrito(),
                ventasPorRango(desde, hasta, canceladas));
    }

    private List<Object[]> leerDistritos(ResultSet rs) throws SQLException {
        List<Object[]> distritos = new ArrayList<Object[]>();
        while (rs.next()) {
            distritos.add(new Object[]{
                rs.getString("distrito"),
                rs.getBigDecimal("total"),
                Integer.valueOf(rs.getInt("ventas"))
            });
        }
        return distritos;
    }

    private List<Object[]> leerVentas(ResultSet rs) throws SQLException {
        List<Object[]> ventas = new ArrayList<Object[]>();
        while (rs.next()) {
            ventas.add(new Object[]{
                rs.getLong("id_venta"),
                rs.getTimestamp("fecha_venta"),
                rs.getString("direccion"),
                rs.getString("dni_ruc"),
                rs.getBigDecimal("total"),
                VentaEstado.normalizar(rs.getString("estado"))
            });
        }
        return ventas;
    }

    public VentaTicket obtenerVentaTicket(long idVenta) throws SQLException {
        String ventaSql = "SELECT v.id_venta, "
                + "COALESCE(v.documento_comprobante, '') AS documento_comprobante, "
                + "v.fecha_venta, v.hora_entrega_pactada, v.total, v.estado, le.id_lugar_entrega, "
                + "le.numero, le.direccion, le.id_distrito, COALESCE(d.nombre, 'Otro') AS distrito "
                + "FROM venta v INNER JOIN lugar_entrega le ON le.id_lugar_entrega = v.id_lugar_entrega "
                + "LEFT JOIN distritos d ON d.id_distrito = le.id_distrito "
                + "WHERE v.id_venta = ?";
        String detalleSql = "SELECT d.id_producto, COALESCE(p.nombre_producto, CONCAT('Producto ', d.id_producto)) AS producto, "
                + "d.precio, d.cantidad "
                + "FROM detalle_venta d LEFT JOIN producto p ON p.id_producto = d.id_producto "
                + "WHERE d.id_venta = ? ORDER BY d.id_detalle";

        try (Connection conn = Conexion.abrir()) {
            LugarEntrega lugarEntrega;
            String documento;
            LocalDateTime fecha;
            LocalDateTime horaEntregaPactada;
            BigDecimal total;
            String estado;

            try (PreparedStatement ps = conn.prepareStatement(ventaSql)) {
                ps.setLong(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("La venta seleccionada ya no existe.");
                    }
                    lugarEntrega = new LugarEntrega(
                            rs.getInt("id_lugar_entrega"),
                            rs.getString("numero"),
                            rs.getString("direccion"),
                            rs.getInt("id_distrito"),
                            rs.getString("distrito"));
                    documento = rs.getString("documento_comprobante");
                    Timestamp timestamp = rs.getTimestamp("fecha_venta");
                    fecha = timestamp == null ? LocalDateTime.now() : timestamp.toLocalDateTime();
                    Timestamp horaEntregaTimestamp = rs.getTimestamp("hora_entrega_pactada");
                    horaEntregaPactada = horaEntregaTimestamp == null ? null : horaEntregaTimestamp.toLocalDateTime();
                    total = rs.getBigDecimal("total");
                    estado = VentaEstado.normalizar(rs.getString("estado"));
                }
            }

            List<LineaVenta> lineas = new ArrayList<LineaVenta>();
            try (PreparedStatement ps = conn.prepareStatement(detalleSql)) {
                ps.setLong(1, idVenta);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        lineas.add(new LineaVenta(
                                rs.getInt("id_producto"),
                                rs.getString("producto"),
                                rs.getBigDecimal("precio"),
                                rs.getInt("cantidad")));
                    }
                }
            }

            if (lineas.isEmpty()) {
                throw new SQLException("La venta seleccionada no tiene detalle para exportar.");
            }
            return new VentaTicket(idVenta, lugarEntrega, documento, fecha, total, lineas, estado, horaEntregaPactada);
        }
    }

    private int entero(String sql) throws SQLException {
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private BigDecimal ventasPorPeriodo(LocalDate desde, LocalDate hastaExclusivo) throws SQLException {
        String sql = "SELECT COALESCE(SUM(total), 0) FROM venta "
                + "WHERE fecha_venta >= ? AND fecha_venta < ? AND estado IN ('VENDIDA', 'PAGADA')";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(hastaExclusivo.atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }
}

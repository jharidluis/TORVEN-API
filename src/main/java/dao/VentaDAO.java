package dao;

import configuracion.Conexion;
import configuracion.SqlIds;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import modelos.LineaVenta;
import modelos.LugarEntrega;
import modelos.Producto;
import modelos.ReservaResumen;
import modelos.VentaEstado;
import modelos.VentaTicket;

public class VentaDAO {
    private final LugarEntregaDAO lugarEntregaDAO = new LugarEntregaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    public VentaTicket registrarVenta(int idLugarEntrega, List<LineaVenta> carrito) throws SQLException {
        return registrarReserva(idLugarEntrega, carrito, null);
    }

    public VentaTicket registrarReserva(int idLugarEntrega, List<LineaVenta> carrito) throws SQLException {
        return registrarReserva(idLugarEntrega, carrito, null);
    }

    public VentaTicket registrarReserva(int idLugarEntrega, List<LineaVenta> carrito, LocalDateTime horaEntregaPactada)
            throws SQLException {
        if (idLugarEntrega <= 0) {
            throw new SQLException("Falta el lugar de entrega.");
        }
        if (carrito == null || carrito.isEmpty()) {
            throw new SQLException("El carrito esta vacio.");
        }

        Connection conn = Conexion.abrir();
        try {
            conn.setAutoCommit(false);

            LugarEntrega lugarEntrega = lugarEntregaDAO.obtenerPorId(conn, idLugarEntrega);
            if (lugarEntrega == null) {
                throw new SQLException("El lugar de entrega seleccionado ya no existe.");
            }
            String documento = "";

            List<LineaVenta> lineasFinales = validarYCongelarPrecios(conn, carrito);
            BigDecimal total = total(lineasFinales);
            long idVenta = insertarVenta(conn, idLugarEntrega, documento, total, VentaEstado.EN_PROCESO,
                    horaEntregaPactada);
            insertarDetallesYDescontarStock(conn, idVenta, lineasFinales);

            conn.commit();
            return new VentaTicket(idVenta, lugarEntrega, documento, LocalDateTime.now(), total,
                    lineasFinales, VentaEstado.EN_PROCESO, horaEntregaPactada);
        } catch (Exception ex) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                ex.addSuppressed(rollbackEx);
            }
            if (ex instanceof SQLException) {
                throw (SQLException) ex;
            }
            throw new SQLException(ex.getMessage(), ex);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // La conexion se cerrara de todos modos.
            }
            conn.close();
        }
    }

    public void cambiarEstadoReserva(long idVenta, String nuevoEstado) throws SQLException {
        String estadoFinal = VentaEstado.normalizar(nuevoEstado);
        if (!VentaEstado.VENDIDA.equals(estadoFinal) && !VentaEstado.CANCELADA.equals(estadoFinal)) {
            throw new SQLException("El estado indicado no es valido para una reserva.");
        }

        Connection conn = Conexion.abrir();
        try {
            conn.setAutoCommit(false);
            String estadoActual = estadoActualBloqueado(conn, idVenta);
            if (VentaEstado.CANCELADA.equals(estadoActual)) {
                throw new SQLException("La reserva ya esta cancelada.");
            }
            if (VentaEstado.VENDIDA.equals(estadoActual)) {
                throw new SQLException("La reserva ya fue marcada como vendida.");
            }

            if (VentaEstado.CANCELADA.equals(estadoFinal)) {
                reponerStockReserva(conn, idVenta);
            }
            actualizarEstado(conn, idVenta, estadoFinal);
            conn.commit();
        } catch (Exception ex) {
            try {
                conn.rollback();
            } catch (SQLException rollbackEx) {
                ex.addSuppressed(rollbackEx);
            }
            if (ex instanceof SQLException) {
                throw (SQLException) ex;
            }
            throw new SQLException(ex.getMessage(), ex);
        } finally {
            try {
                conn.setAutoCommit(true);
            } catch (SQLException ignored) {
                // La conexion se cerrara de todos modos.
            }
            conn.close();
        }
    }

    public List<Object[]> listarReservasEnProceso() throws SQLException {
        try {
            return listarReservasConProcedimiento();
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1305) {
                throw ex;
            }
            return listarReservasSinProcedimiento();
        }
    }

    private List<Object[]> listarReservasConProcedimiento() throws SQLException {
        List<Object[]> reservas = new ArrayList<Object[]>();
        try (Connection conn = Conexion.abrir();
             CallableStatement call = conn.prepareCall("{CALL sp_listar_reservas()}");
             ResultSet rs = call.executeQuery()) {
            while (rs.next()) {
                reservas.add(mapearReserva(rs));
            }
        }
        return reservas;
    }

    private List<Object[]> listarReservasSinProcedimiento() throws SQLException {
        List<Object[]> reservas = new ArrayList<Object[]>();
        String sql = "SELECT v.id_venta, v.fecha_venta, le.direccion, "
                + "COALESCE(v.documento_comprobante, '') AS dni_ruc, v.total "
                + "FROM venta v INNER JOIN lugar_entrega le ON le.id_lugar_entrega = v.id_lugar_entrega "
                + "WHERE v.estado = ? "
                + "ORDER BY v.fecha_venta ASC";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, VentaEstado.EN_PROCESO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    reservas.add(mapearReserva(rs));
                }
            }
        }
        return reservas;
    }

    public List<ReservaResumen> listarReservasWeb() throws SQLException {
        List<ReservaResumen> reservas = new ArrayList<ReservaResumen>();
        String sql = "SELECT v.id_venta, v.fecha_venta, v.hora_entrega_pactada, v.total, v.estado, "
                + "le.numero, le.direccion, COALESCE(d.nombre, 'Otro') AS distrito "
                + "FROM venta v INNER JOIN lugar_entrega le ON le.id_lugar_entrega = v.id_lugar_entrega "
                + "LEFT JOIN distritos d ON d.id_distrito = le.id_distrito "
                + "WHERE v.estado = ? "
                + "ORDER BY v.hora_entrega_pactada ASC, v.fecha_venta ASC";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, VentaEstado.EN_PROCESO);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp fecha = rs.getTimestamp("fecha_venta");
                    Timestamp horaEntrega = rs.getTimestamp("hora_entrega_pactada");
                    reservas.add(new ReservaResumen(
                            rs.getLong("id_venta"),
                            fecha == null ? null : fecha.toLocalDateTime(),
                            horaEntrega == null ? null : horaEntrega.toLocalDateTime(),
                            rs.getString("numero"),
                            rs.getString("direccion"),
                            rs.getString("distrito"),
                            rs.getBigDecimal("total"),
                            rs.getString("estado")));
                }
            }
        }
        return reservas;
    }

    private Object[] mapearReserva(ResultSet rs) throws SQLException {
        return new Object[]{
            rs.getLong("id_venta"),
            rs.getTimestamp("fecha_venta"),
            rs.getString("direccion"),
            rs.getString("dni_ruc"),
            rs.getBigDecimal("total")
        };
    }

    private List<LineaVenta> validarYCongelarPrecios(Connection conn, List<LineaVenta> carrito) throws SQLException {
        List<LineaVenta> lineas = new ArrayList<LineaVenta>();
        for (LineaVenta linea : carrito) {
            if (linea.getCantidad() <= 0) {
                throw new SQLException("Hay productos con cantidad invalida.");
            }
            Producto producto = productoDAO.obtenerPorId(conn, linea.getIdProducto(), true);
            if (producto == null) {
                throw new SQLException("Un producto del carrito ya no existe.");
            }
            if (producto.getStock() < linea.getCantidad()) {
                throw new SQLException("No hay stock suficiente para " + producto.getNombre()
                        + ". Disponible: " + producto.getStock());
            }
            lineas.add(new LineaVenta(
                    producto.getId(),
                    producto.getNombre(),
                    producto.getPrecio(),
                    linea.getCantidad()
            ));
        }
        return lineas;
    }

    private long insertarVenta(Connection conn, int idLugarEntrega, String documento, BigDecimal total, String estado,
            LocalDateTime horaEntregaPactada) throws SQLException {
        if (SqlIds.requiereIdManual(conn, "venta", "id_venta")) {
            return insertarVentaConId(conn, idLugarEntrega, documento, total, estado, horaEntregaPactada);
        }

        String sql = "INSERT INTO venta(id_lugar_entrega, documento_comprobante, fecha_venta, total, estado, "
                + "hora_entrega_pactada) VALUES (?, ?, NOW(), ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idLugarEntrega);
            ps.setString(2, documento);
            ps.setBigDecimal(3, total);
            ps.setString(4, estado);
            ps.setTimestamp(5, horaEntregaPactada == null ? null : Timestamp.valueOf(horaEntregaPactada));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No se genero el numero de venta.");
    }

    private long insertarVentaConId(Connection conn, int idLugarEntrega, String documento, BigDecimal total, String estado,
            LocalDateTime horaEntregaPactada) throws SQLException {
        long idVenta = SqlIds.siguienteLong(conn, "venta", "id_venta");
        String sql = "INSERT INTO venta(id_venta, id_lugar_entrega, documento_comprobante, fecha_venta, total, estado, "
                + "hora_entrega_pactada) VALUES (?, ?, ?, NOW(), ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idVenta);
            ps.setInt(2, idLugarEntrega);
            ps.setString(3, documento);
            ps.setBigDecimal(4, total);
            ps.setString(5, estado);
            ps.setTimestamp(6, horaEntregaPactada == null ? null : Timestamp.valueOf(horaEntregaPactada));
            ps.executeUpdate();
        }
        return idVenta;
    }

    private void insertarDetallesYDescontarStock(Connection conn, long idVenta, List<LineaVenta> lineas)
            throws SQLException {
        boolean detalleIdManual = SqlIds.requiereIdManual(conn, "detalle_venta", "id_detalle");
        String insertDetalle = detalleIdManual
                ? "INSERT INTO detalle_venta(id_detalle, id_venta, id_producto, cantidad, precio, subtotal) "
                + "VALUES (?, ?, ?, ?, ?, ?)"
                : "INSERT INTO detalle_venta(id_venta, id_producto, cantidad, precio, subtotal) "
                + "VALUES (?, ?, ?, ?, ?)";
        String updateStock = "UPDATE producto SET stock = stock - ? "
                + "WHERE id_producto = ? AND activo = 1 AND stock >= ?";

        try (PreparedStatement detalle = conn.prepareStatement(insertDetalle);
             PreparedStatement stock = conn.prepareStatement(updateStock)) {
            for (LineaVenta linea : lineas) {
                int parametro = 1;
                if (detalleIdManual) {
                    detalle.setLong(parametro++, SqlIds.siguienteLong(conn, "detalle_venta", "id_detalle"));
                }
                detalle.setLong(parametro++, idVenta);
                detalle.setInt(parametro++, linea.getIdProducto());
                detalle.setInt(parametro++, linea.getCantidad());
                detalle.setBigDecimal(parametro++, linea.getPrecio());
                detalle.setBigDecimal(parametro, linea.getSubtotal());
                detalle.executeUpdate();

                stock.setInt(1, linea.getCantidad());
                stock.setInt(2, linea.getIdProducto());
                stock.setInt(3, linea.getCantidad());
                int filas = stock.executeUpdate();
                if (filas != 1) {
                    throw new SQLException("No se pudo descontar stock de " + linea.getNombreProducto());
                }
            }
        }
    }

    private String estadoActualBloqueado(Connection conn, long idVenta) throws SQLException {
        String sql = "SELECT estado FROM venta WHERE id_venta = ? FOR UPDATE";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idVenta);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return VentaEstado.normalizar(rs.getString("estado"));
                }
            }
        }
        throw new SQLException("La reserva seleccionada ya no existe.");
    }

    private void reponerStockReserva(Connection conn, long idVenta) throws SQLException {
        String sql = "UPDATE producto p "
                + "INNER JOIN detalle_venta d ON d.id_producto = p.id_producto "
                + "SET p.stock = p.stock + d.cantidad "
                + "WHERE d.id_venta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idVenta);
            ps.executeUpdate();
        }
    }

    private void actualizarEstado(Connection conn, long idVenta, String estado) throws SQLException {
        String sql = "UPDATE venta SET estado = ? WHERE id_venta = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, estado);
            ps.setLong(2, idVenta);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("No se pudo actualizar el estado de la reserva.");
            }
        }
    }

    private BigDecimal total(List<LineaVenta> lineas) {
        BigDecimal total = BigDecimal.ZERO;
        for (LineaVenta linea : lineas) {
            total = total.add(linea.getSubtotal());
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }
}

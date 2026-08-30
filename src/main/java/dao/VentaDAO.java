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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import modelos.Cliente;
import modelos.LineaVenta;
import modelos.Producto;
import modelos.VentaEstado;
import modelos.VentaTicket;

public class VentaDAO {
    private final ClienteDAO clienteDAO = new ClienteDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();

    public VentaTicket registrarVenta(int idCliente, List<LineaVenta> carrito) throws SQLException {
        return registrarReserva(idCliente, carrito);
    }

    public VentaTicket registrarReserva(int idCliente, List<LineaVenta> carrito) throws SQLException {
        if (idCliente <= 0) {
            throw new SQLException("Selecciona un cliente.");
        }
        if (carrito == null || carrito.isEmpty()) {
            throw new SQLException("El carrito esta vacio.");
        }

        Connection conn = Conexion.abrir();
        try {
            conn.setAutoCommit(false);

            Cliente cliente = clienteDAO.obtenerPorId(conn, idCliente);
            if (cliente == null) {
                throw new SQLException("El cliente seleccionado ya no existe.");
            }
            String documento = limpiarDocumento(cliente);

            List<LineaVenta> lineasFinales = validarYCongelarPrecios(conn, carrito);
            BigDecimal total = total(lineasFinales);
            long idVenta = insertarVenta(conn, idCliente, documento, total, VentaEstado.EN_PROCESO);
            insertarDetallesYDescontarStock(conn, idVenta, lineasFinales);

            conn.commit();
            return new VentaTicket(idVenta, cliente, documento, LocalDateTime.now(), total,
                    lineasFinales, VentaEstado.EN_PROCESO);
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
        String sql = "SELECT v.id_venta, v.fecha_venta, c.nombre_completo, "
                + "COALESCE(NULLIF(v.documento_comprobante, ''), c.dni_ruc) AS dni_ruc, v.total "
                + "FROM venta v INNER JOIN cliente c ON c.id_cliente = v.id_cliente "
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

    private Object[] mapearReserva(ResultSet rs) throws SQLException {
        return new Object[]{
            rs.getLong("id_venta"),
            rs.getTimestamp("fecha_venta"),
            rs.getString("nombre_completo"),
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

    private long insertarVenta(Connection conn, int idCliente, String documento, BigDecimal total, String estado)
            throws SQLException {
        if (SqlIds.requiereIdManual(conn, "venta", "id_venta")) {
            return insertarVentaConId(conn, idCliente, documento, total, estado);
        }

        String sql = "INSERT INTO venta(id_cliente, documento_comprobante, fecha_venta, total, estado) "
                + "VALUES (?, ?, NOW(), ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, idCliente);
            ps.setString(2, documento);
            ps.setBigDecimal(3, total);
            ps.setString(4, estado);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        throw new SQLException("No se genero el numero de venta.");
    }

    private long insertarVentaConId(Connection conn, int idCliente, String documento, BigDecimal total, String estado)
            throws SQLException {
        long idVenta = SqlIds.siguienteLong(conn, "venta", "id_venta");
        String sql = "INSERT INTO venta(id_venta, id_cliente, documento_comprobante, fecha_venta, total, estado) "
                + "VALUES (?, ?, ?, NOW(), ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, idVenta);
            ps.setInt(2, idCliente);
            ps.setString(3, documento);
            ps.setBigDecimal(4, total);
            ps.setString(5, estado);
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

    private String limpiarDocumento(Cliente cliente) throws SQLException {
        String documento = cliente == null ? "" : cliente.getDniRuc();
        documento = documento == null ? "" : documento.trim();
        documento = documento.trim();
        if (!documento.isEmpty() && !documento.matches("[0-9]{8}|[0-9]{11}")) {
            throw new SQLException("Revisa el DNI/RUC del cliente. Debe tener 8 u 11 digitos.");
        }
        return documento;
    }
}

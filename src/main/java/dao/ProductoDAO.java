package dao;

import configuracion.Conexion;
import configuracion.SqlIds;
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import modelos.Producto;

public class ProductoDAO {
    public List<Producto> listar(String filtro) throws SQLException {
        String texto = filtro == null ? "" : filtro.trim();
        try {
            return buscarConProcedimiento(texto);
        } catch (SQLException ex) {
            if (ex.getErrorCode() != 1305) {
                throw ex;
            }
            return listarPorEstado(texto, true);
        }
    }

    public List<Producto> listarEliminados() throws SQLException {
        return listarPorEstado("", false);
    }

    private List<Producto> listarPorEstado(String texto, boolean activo) throws SQLException {
        List<Producto> productos = new ArrayList<Producto>();
        String sql = "SELECT id_producto, nombre_producto, precio_producto, stock "
                + "FROM producto "
                + "WHERE activo = ? AND (? = '' OR nombre_producto LIKE ?) "
                + "ORDER BY nombre_producto";

        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setString(2, texto);
            ps.setString(3, "%" + texto + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    productos.add(mapear(rs));
                }
            }
        }
        return productos;
    }

    private List<Producto> buscarConProcedimiento(String texto) throws SQLException {
        List<Producto> productos = new ArrayList<Producto>();
        try (Connection conn = Conexion.abrir();
             CallableStatement call = conn.prepareCall("{CALL sp_buscar_productos(?)}")) {
            call.setString(1, texto);
            try (ResultSet rs = call.executeQuery()) {
                while (rs.next()) {
                    productos.add(mapear(rs));
                }
            }
        }
        return productos;
    }

    public Producto obtenerPorId(Connection conn, int id, boolean bloquear) throws SQLException {
        String sql = "SELECT id_producto, nombre_producto, precio_producto, stock "
                + "FROM producto WHERE id_producto = ? AND activo = 1" + (bloquear ? " FOR UPDATE" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Producto obtenerPorId(int id) throws SQLException {
        try (Connection conn = Conexion.abrir()) {
            return obtenerPorId(conn, id, false);
        }
    }

    public void guardar(Producto producto) throws SQLException {
        validar(producto);
        try (Connection conn = Conexion.abrir()) {
            if (existeNombre(conn, producto.getNombre(), producto.getId())) {
                throw new SQLException("Ya existe un producto con ese nombre.");
            }
            if (producto.getId() == 0) {
                insertar(conn, producto);
            } else {
                actualizar(conn, producto);
            }
        }
    }

    public void eliminar(int id) throws SQLException {
        if (id <= 0) {
            throw new SQLException("Selecciona un producto.");
        }
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE producto SET activo = 0 WHERE id_producto = ? AND activo = 1")) {
            ps.setInt(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("El producto ya no existe o ya esta deshabilitado.");
            }
        }
    }

    public void reactivar(int id) throws SQLException {
        if (id <= 0) {
            throw new SQLException("Selecciona un producto eliminado.");
        }
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE producto SET activo = 1 WHERE id_producto = ? AND activo = 0")) {
            ps.setInt(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("El producto ya esta activo o ya no existe.");
            }
        }
    }

    private void insertar(Connection conn, Producto producto) throws SQLException {
        if (SqlIds.requiereIdManual(conn, "producto", "id_producto")) {
            insertarConId(conn, producto);
            return;
        }

        String sql = "INSERT INTO producto(nombre_producto, precio_producto, stock, activo) VALUES (?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, producto.getNombre());
            ps.setBigDecimal(2, producto.getPrecio());
            ps.setInt(3, producto.getStock());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    producto.setId(keys.getInt(1));
                }
            }
        }
    }

    private void insertarConId(Connection conn, Producto producto) throws SQLException {
        int id = SqlIds.siguienteInt(conn, "producto", "id_producto");
        String sql = "INSERT INTO producto(id_producto, nombre_producto, precio_producto, stock, activo) "
                + "VALUES (?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, producto.getNombre());
            ps.setBigDecimal(3, producto.getPrecio());
            ps.setInt(4, producto.getStock());
            ps.executeUpdate();
            producto.setId(id);
        }
    }

    private void actualizar(Connection conn, Producto producto) throws SQLException {
        String sql = "UPDATE producto SET nombre_producto = ?, precio_producto = ?, stock = ?, activo = 1 "
                + "WHERE id_producto = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, producto.getNombre());
            ps.setBigDecimal(2, producto.getPrecio());
            ps.setInt(3, producto.getStock());
            ps.setInt(4, producto.getId());
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("El producto ya no existe.");
            }
        }
    }

    private boolean existeNombre(Connection conn, String nombre, int idActual) throws SQLException {
        // La base usa collation utf8mb4_unicode_ci (ya es case-insensitive), asi que
        // comparar directo permite usar el indice; envolver en LOWER() obligaba a
        // MySQL a recorrer toda la tabla producto por cada guardado.
        String sql = "SELECT COUNT(*) FROM producto WHERE nombre_producto = ? AND id_producto <> ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, nombre);
            ps.setInt(2, idActual);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    private Producto mapear(ResultSet rs) throws SQLException {
        return new Producto(
                rs.getInt("id_producto"),
                rs.getString("nombre_producto"),
                rs.getBigDecimal("precio_producto"),
                rs.getInt("stock")
        );
    }

    private void validar(Producto producto) throws SQLException {
        if (producto.getNombre() == null || producto.getNombre().trim().isEmpty()) {
            throw new SQLException("Ingresa el nombre del producto.");
        }
        producto.setNombre(producto.getNombre().trim());
        BigDecimal precio = producto.getPrecio();
        if (precio == null || precio.compareTo(BigDecimal.ZERO) <= 0) {
            throw new SQLException("El precio debe ser mayor a cero.");
        }
        if (producto.getStock() < 0) {
            throw new SQLException("El stock no puede ser negativo.");
        }
    }
}

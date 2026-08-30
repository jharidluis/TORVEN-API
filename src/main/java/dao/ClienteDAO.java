package dao;

import configuracion.Conexion;
import configuracion.SqlIds;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import modelos.Cliente;
import modelos.Distrito;

public class ClienteDAO {
    public List<Cliente> listar(String filtro) throws SQLException {
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

    public List<Cliente> listarEliminados() throws SQLException {
        return listarPorEstado("", false);
    }

    private List<Cliente> listarPorEstado(String texto, boolean activo) throws SQLException {
        List<Cliente> clientes = new ArrayList<Cliente>();
        String sql = "SELECT c.id_cliente, c.nombre_completo, c.numero, c.dni_ruc, c.direccion, "
                + "c.id_distrito, COALESCE(d.nombre, 'Otro') AS distrito "
                + "FROM cliente c LEFT JOIN distritos d ON d.id_distrito = c.id_distrito "
                + "WHERE c.activo = ? AND (? = '' OR c.nombre_completo LIKE ? OR c.numero LIKE ? "
                + "OR c.dni_ruc LIKE ? OR d.nombre LIKE ?) "
                + "ORDER BY c.nombre_completo";

        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, activo ? 1 : 0);
            ps.setString(2, texto);
            ps.setString(3, "%" + texto + "%");
            ps.setString(4, "%" + texto + "%");
            ps.setString(5, "%" + texto + "%");
            ps.setString(6, "%" + texto + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapear(rs));
                }
            }
        }
        return clientes;
    }

    private List<Cliente> buscarConProcedimiento(String texto) throws SQLException {
        List<Cliente> clientes = new ArrayList<Cliente>();
        try (Connection conn = Conexion.abrir();
             CallableStatement call = conn.prepareCall("{CALL sp_buscar_clientes(?)}")) {
            call.setString(1, texto);
            try (ResultSet rs = call.executeQuery()) {
                while (rs.next()) {
                    clientes.add(mapear(rs));
                }
            }
        }
        return clientes;
    }

    public Cliente obtenerPorId(Connection conn, int id) throws SQLException {
        String sql = "SELECT c.id_cliente, c.nombre_completo, c.numero, c.dni_ruc, c.direccion, "
                + "c.id_distrito, COALESCE(d.nombre, 'Otro') AS distrito "
                + "FROM cliente c LEFT JOIN distritos d ON d.id_distrito = c.id_distrito "
                + "WHERE c.id_cliente = ? AND c.activo = 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public Cliente obtenerPorId(int id) throws SQLException {
        try (Connection conn = Conexion.abrir()) {
            return obtenerPorId(conn, id);
        }
    }

    public void guardar(Cliente cliente) throws SQLException {
        validar(cliente);
        try (Connection conn = Conexion.abrir()) {
            if (cliente.getIdDistrito() <= 0) {
                cliente.setIdDistrito(asegurarDistrito(conn, cliente.getDistrito()));
            }
            if (cliente.getId() == 0) {
                insertar(conn, cliente);
            } else {
                actualizar(conn, cliente);
            }
        }
    }

    public List<Distrito> listarDistritos() throws SQLException {
        List<Distrito> distritos = new ArrayList<Distrito>();
        String sql = "SELECT id_distrito, nombre FROM distritos "
                + "ORDER BY CASE WHEN nombre = 'Otro' THEN 1 ELSE 0 END, nombre";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                distritos.add(new Distrito(
                        rs.getInt("id_distrito"),
                        rs.getString("nombre")));
            }
        }
        return distritos;
    }

    public void eliminar(int id) throws SQLException {
        if (id <= 0) {
            throw new SQLException("Selecciona un cliente.");
        }
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE cliente SET activo = 0 WHERE id_cliente = ? AND activo = 1")) {
            ps.setInt(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("El cliente ya no existe o ya esta deshabilitado.");
            }
        }
    }

    public void reactivar(int id) throws SQLException {
        if (id <= 0) {
            throw new SQLException("Selecciona un cliente eliminado.");
        }
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE cliente SET activo = 1 WHERE id_cliente = ? AND activo = 0")) {
            ps.setInt(1, id);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("El cliente ya esta activo o ya no existe.");
            }
        }
    }

    private void insertar(Connection conn, Cliente cliente) throws SQLException {
        if (SqlIds.requiereIdManual(conn, "cliente", "id_cliente")) {
            insertarConId(conn, cliente);
            return;
        }

        String sql = "INSERT INTO cliente(nombre_completo, numero, dni_ruc, direccion, id_distrito, activo) "
                + "VALUES (?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, cliente.getNombre());
            ps.setString(2, cliente.getNumero());
            ps.setString(3, cliente.getDniRuc());
            ps.setString(4, cliente.getDireccion());
            ps.setInt(5, cliente.getIdDistrito());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    cliente.setId(keys.getInt(1));
                }
            }
        }
    }

    private void insertarConId(Connection conn, Cliente cliente) throws SQLException {
        int id = SqlIds.siguienteInt(conn, "cliente", "id_cliente");
        String sql = "INSERT INTO cliente(id_cliente, nombre_completo, numero, dni_ruc, direccion, id_distrito, activo) "
                + "VALUES (?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, cliente.getNombre());
            ps.setString(3, cliente.getNumero());
            ps.setString(4, cliente.getDniRuc());
            ps.setString(5, cliente.getDireccion());
            ps.setInt(6, cliente.getIdDistrito());
            ps.executeUpdate();
            cliente.setId(id);
        }
    }

    private void actualizar(Connection conn, Cliente cliente) throws SQLException {
        String sql = "UPDATE cliente SET nombre_completo = ?, numero = ?, dni_ruc = ?, "
                + "direccion = ?, id_distrito = ?, activo = 1 WHERE id_cliente = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, cliente.getNombre());
            ps.setString(2, cliente.getNumero());
            ps.setString(3, cliente.getDniRuc());
            ps.setString(4, cliente.getDireccion());
            ps.setInt(5, cliente.getIdDistrito());
            ps.setInt(6, cliente.getId());
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("El cliente ya no existe.");
            }
        }
    }

    private int asegurarDistrito(Connection conn, String nombreDistrito) throws SQLException {
        String nombre = limpiar(nombreDistrito);
        if (nombre.isEmpty()) {
            nombre = "Otro";
        }

        // Collation utf8mb4_unicode_ci ya es case-insensitive; comparar sin LOWER()
        // permite usar el indice de nombre en vez de escanear toda la tabla.
        String existe = "SELECT id_distrito FROM distritos WHERE nombre = ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(existe)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id_distrito");
                }
            }
        }

        if (SqlIds.requiereIdManual(conn, "distritos", "id_distrito")) {
            int id = SqlIds.siguienteInt(conn, "distritos", "id_distrito");
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO distritos(id_distrito, nombre) VALUES (?, ?)")) {
                ps.setInt(1, id);
                ps.setString(2, nombre);
                ps.executeUpdate();
            }
            return id;
        }

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO distritos(nombre) VALUES (?)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nombre);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }

        try (PreparedStatement ps = conn.prepareStatement(existe)) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("id_distrito");
                }
            }
        }
        throw new SQLException("No se pudo registrar el distrito del cliente.");
    }

    private Cliente mapear(ResultSet rs) throws SQLException {
        return new Cliente(
                rs.getInt("id_cliente"),
                rs.getString("nombre_completo"),
                rs.getString("numero"),
                rs.getString("dni_ruc"),
                rs.getString("direccion"),
                rs.getInt("id_distrito"),
                rs.getString("distrito")
        );
    }

    private void validar(Cliente cliente) throws SQLException {
        if (cliente.getNombre() == null || cliente.getNombre().trim().isEmpty()) {
            throw new SQLException("Ingresa el nombre del cliente.");
        }
        if (cliente.getNumero() == null || cliente.getNumero().trim().isEmpty()) {
            throw new SQLException("Ingresa el telefono del cliente.");
        }
        if (cliente.getDireccion() == null || cliente.getDireccion().trim().isEmpty()) {
            throw new SQLException("Ingresa la direccion del cliente.");
        }
        cliente.setNombre(limpiar(cliente.getNombre()));
        cliente.setNumero(limpiar(cliente.getNumero()));
        cliente.setDniRuc(limpiar(cliente.getDniRuc()));
        cliente.setDireccion(limpiar(cliente.getDireccion()));
        cliente.setDistrito(limpiar(cliente.getDistrito()));
        if (cliente.getDistrito().isEmpty() && cliente.getIdDistrito() <= 0) {
            cliente.setDistrito("Otro");
        }
        if (cliente.getNumero().length() < 6 || cliente.getNumero().length() > 20) {
            throw new SQLException("El telefono debe tener entre 6 y 20 caracteres.");
        }
        if (!cliente.getDniRuc().isEmpty() && !cliente.getDniRuc().matches("[0-9]{8}|[0-9]{11}")) {
            throw new SQLException("El DNI debe tener 8 digitos o el RUC 11 digitos.");
        }
    }

    private String limpiar(String value) {
        return value == null ? "" : value.trim();
    }
}

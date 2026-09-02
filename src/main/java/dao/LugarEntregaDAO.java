package dao;

import configuracion.Conexion;
import configuracion.SqlIds;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import modelos.Distrito;
import modelos.LugarEntrega;

public class LugarEntregaDAO {
    public LugarEntrega obtenerPorId(Connection conn, int id) throws SQLException {
        String sql = "SELECT le.id_lugar_entrega, le.numero, le.direccion, "
                + "le.id_distrito, COALESCE(d.nombre, 'Otro') AS distrito "
                + "FROM lugar_entrega le LEFT JOIN distritos d ON d.id_distrito = le.id_distrito "
                + "WHERE le.id_lugar_entrega = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public LugarEntrega obtenerPorId(int id) throws SQLException {
        try (Connection conn = Conexion.abrir()) {
            return obtenerPorId(conn, id);
        }
    }

    public void crear(LugarEntrega lugarEntrega) throws SQLException {
        validar(lugarEntrega);
        try (Connection conn = Conexion.abrir()) {
            if (SqlIds.requiereIdManual(conn, "lugar_entrega", "id_lugar_entrega")) {
                insertarConId(conn, lugarEntrega);
                return;
            }

            String sql = "INSERT INTO lugar_entrega(numero, direccion, id_distrito) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, lugarEntrega.getNumero().isEmpty() ? null : lugarEntrega.getNumero());
                ps.setString(2, lugarEntrega.getDireccion());
                ps.setInt(3, lugarEntrega.getIdDistrito());
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        lugarEntrega.setId(keys.getInt(1));
                    }
                }
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

    private void insertarConId(Connection conn, LugarEntrega lugarEntrega) throws SQLException {
        int id = SqlIds.siguienteInt(conn, "lugar_entrega", "id_lugar_entrega");
        String sql = "INSERT INTO lugar_entrega(id_lugar_entrega, numero, direccion, id_distrito) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.setString(2, lugarEntrega.getNumero().isEmpty() ? null : lugarEntrega.getNumero());
            ps.setString(3, lugarEntrega.getDireccion());
            ps.setInt(4, lugarEntrega.getIdDistrito());
            ps.executeUpdate();
            lugarEntrega.setId(id);
        }
    }

    private LugarEntrega mapear(ResultSet rs) throws SQLException {
        return new LugarEntrega(
                rs.getInt("id_lugar_entrega"),
                rs.getString("numero"),
                rs.getString("direccion"),
                rs.getInt("id_distrito"),
                rs.getString("distrito")
        );
    }

    private void validar(LugarEntrega lugarEntrega) throws SQLException {
        if (lugarEntrega.getDireccion() == null || lugarEntrega.getDireccion().trim().isEmpty()) {
            throw new SQLException("Ingresa la direccion de entrega.");
        }
        if (lugarEntrega.getIdDistrito() <= 0) {
            throw new SQLException("Selecciona el distrito de entrega.");
        }
        lugarEntrega.setNumero(limpiar(lugarEntrega.getNumero()));
        lugarEntrega.setDireccion(limpiar(lugarEntrega.getDireccion()));
        if (!lugarEntrega.getNumero().isEmpty()
                && (lugarEntrega.getNumero().length() < 6 || lugarEntrega.getNumero().length() > 20)) {
            throw new SQLException("El telefono debe tener entre 6 y 20 caracteres.");
        }
    }

    private String limpiar(String value) {
        return value == null ? "" : value.trim();
    }
}

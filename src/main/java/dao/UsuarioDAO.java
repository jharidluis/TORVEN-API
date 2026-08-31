package dao;

import configuracion.Conexion;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import modelos.Usuario;
import seguridad.PasswordUtil;

public class UsuarioDAO {
    public Usuario autenticar(String usuario, char[] clave) throws SQLException {
        String sql = "SELECT id_usuario, usuario, nombre, rol, clave_hash, debe_cambiar_clave "
                + "FROM usuario WHERE usuario = ? AND activo = 1";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, limpiar(usuario));
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                String hashIngresado = PasswordUtil.hash(new String(clave)).toLowerCase();
                String hashGuardado = rs.getString("clave_hash");
                hashGuardado = hashGuardado == null ? "" : hashGuardado.toLowerCase();
                boolean coincide = MessageDigest.isEqual(
                        hashIngresado.getBytes(StandardCharsets.UTF_8),
                        hashGuardado.getBytes(StandardCharsets.UTF_8));
                if (!coincide) {
                    return null;
                }
                return new Usuario(
                        rs.getInt("id_usuario"),
                        rs.getString("usuario"),
                        rs.getString("nombre"),
                        rs.getString("rol"),
                        rs.getBoolean("debe_cambiar_clave"));
            }
        }
    }

    public void cambiarClave(int idUsuario, char[] nuevaClave) throws SQLException {
        String clave = new String(nuevaClave);
        validarClave(clave);

        String sql = "UPDATE usuario SET clave_hash = ?, debe_cambiar_clave = 0 WHERE id_usuario = ?";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, PasswordUtil.hash(clave));
            ps.setInt(2, idUsuario);
            int filas = ps.executeUpdate();
            if (filas == 0) {
                throw new SQLException("No se pudo actualizar la clave del usuario.");
            }
        }
    }

    public void cambiarUsuarioYClave(int idUsuario, String nuevoUsuario, char[] nuevaClave) throws SQLException {
        String usuarioLimpio = limpiar(nuevoUsuario);
        String clave = new String(nuevaClave);
        if (usuarioLimpio.isEmpty()) {
            throw new SQLException("Ingresa el nuevo usuario.");
        }
        if (usuarioLimpio.length() < 4 || usuarioLimpio.length() > 40) {
            throw new SQLException("El usuario debe tener entre 4 y 40 caracteres.");
        }
        if (!usuarioLimpio.matches("[A-Za-z0-9._-]+")) {
            throw new SQLException("El usuario solo puede tener letras, numeros, punto, guion o guion bajo.");
        }
        validarClave(clave);

        String sql = "UPDATE usuario SET usuario = ?, clave_hash = ?, debe_cambiar_clave = 0 WHERE id_usuario = ?";
        try (Connection conn = Conexion.abrir();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (usuarioExiste(conn, usuarioLimpio, idUsuario)) {
                throw new SQLException("El usuario '" + usuarioLimpio + "' ya esta en uso.");
            }
            ps.setString(1, usuarioLimpio);
            ps.setString(2, PasswordUtil.hash(clave));
            ps.setInt(3, idUsuario);
            try {
                int filas = ps.executeUpdate();
                if (filas == 0) {
                    throw new SQLException("No se pudo actualizar el acceso del usuario.");
                }
            } catch (SQLException ex) {
                if ("23000".equals(ex.getSQLState())) {
                    throw new SQLException("El usuario '" + usuarioLimpio + "' ya esta en uso.", ex);
                }
                throw ex;
            }
        }
    }

    private boolean usuarioExiste(Connection conn, String usuario, int idUsuarioActual) throws SQLException {
        String sql = "SELECT 1 FROM usuario WHERE LOWER(usuario) = LOWER(?) AND id_usuario <> ? LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, usuario);
            ps.setInt(2, idUsuarioActual);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void validarClave(String clave) throws SQLException {
        if (clave == null || clave.trim().length() < 6) {
            throw new SQLException("La nueva clave debe tener al menos 6 caracteres.");
        }
    }

    private String limpiar(String value) {
        return value == null ? "" : value.trim();
    }
}

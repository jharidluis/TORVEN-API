package api;

import modelos.Usuario;

public class LoginResponse {
    public final String token;
    public final int id;
    public final String usuario;
    public final String nombre;
    public final String rol;
    public final boolean debeCambiarClave;

    public LoginResponse(String token, Usuario usuario) {
        this.token = token;
        this.id = usuario.getId();
        this.usuario = usuario.getUsuario();
        this.nombre = usuario.getNombre();
        this.rol = usuario.getRol();
        this.debeCambiarClave = usuario.debeCambiarClave();
    }
}

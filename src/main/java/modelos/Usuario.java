package modelos;

public class Usuario {
    public static final String ROL_ADMIN = "ADMIN";
    public static final String ROL_VENDEDOR = "VENDEDOR";

    private final int id;
    private final String usuario;
    private final String nombre;
    private final String rol;
    private final boolean debeCambiarClave;

    public Usuario(int id, String usuario, String nombre, String rol, boolean debeCambiarClave) {
        this.id = id;
        this.usuario = usuario;
        this.nombre = nombre;
        this.rol = rol;
        this.debeCambiarClave = debeCambiarClave;
    }

    public int getId() {
        return id;
    }

    public String getUsuario() {
        return usuario;
    }

    public String getNombre() {
        return nombre;
    }

    public String getRol() {
        return rol;
    }

    public boolean debeCambiarClave() {
        return debeCambiarClave;
    }

    public boolean esAdministrador() {
        return ROL_ADMIN.equalsIgnoreCase(rol);
    }
}

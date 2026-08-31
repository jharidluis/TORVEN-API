package modelos;

public class Cliente {
    private int id;
    private String nombre;
    private String numero;
    private String direccion;
    private int idDistrito;
    private String distrito;

    public Cliente() {
    }

    public Cliente(int id, String nombre, String numero, String direccion) {
        this(id, nombre, numero, direccion, 0, "");
    }

    public Cliente(int id, String nombre, String numero, String direccion,
            int idDistrito, String distrito) {
        this.id = id;
        this.nombre = nombre;
        this.numero = numero;
        this.direccion = direccion;
        this.idDistrito = idDistrito;
        this.distrito = distrito;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public int getIdDistrito() {
        return idDistrito;
    }

    public void setIdDistrito(int idDistrito) {
        this.idDistrito = idDistrito;
    }

    public String getDistrito() {
        return distrito;
    }

    public void setDistrito(String distrito) {
        this.distrito = distrito;
    }
}

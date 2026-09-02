package modelos;

public class LugarEntrega {
    private int id;
    private String numero;
    private String direccion;
    private int idDistrito;
    private String distrito;

    public LugarEntrega() {
    }

    public LugarEntrega(int id, String numero, String direccion, int idDistrito, String distrito) {
        this.id = id;
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

package modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Gasto {
    private int id;
    private int idCategoriaGasto;
    private String categoria;
    private String descripcion;
    private BigDecimal monto;
    private Integer idUsuario;
    private String usuario;
    private LocalDateTime creadoEn;

    public Gasto() {
    }

    public Gasto(int id, int idCategoriaGasto, String categoria, String descripcion, BigDecimal monto,
            Integer idUsuario, String usuario, LocalDateTime creadoEn) {
        this.id = id;
        this.idCategoriaGasto = idCategoriaGasto;
        this.categoria = categoria;
        this.descripcion = descripcion;
        this.monto = monto;
        this.idUsuario = idUsuario;
        this.usuario = usuario;
        this.creadoEn = creadoEn;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getIdCategoriaGasto() {
        return idCategoriaGasto;
    }

    public void setIdCategoriaGasto(int idCategoriaGasto) {
        this.idCategoriaGasto = idCategoriaGasto;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public Integer getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(Integer idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(LocalDateTime creadoEn) {
        this.creadoEn = creadoEn;
    }
}

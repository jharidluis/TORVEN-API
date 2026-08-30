package modelos;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class LineaVenta {
    private final int idProducto;
    private final String nombreProducto;
    private final BigDecimal precio;
    private int cantidad;

    public LineaVenta(int idProducto, String nombreProducto, BigDecimal precio, int cantidad) {
        this.idProducto = idProducto;
        this.nombreProducto = nombreProducto;
        this.precio = precio.setScale(2, RoundingMode.HALF_UP);
        this.cantidad = cantidad;
    }

    public int getIdProducto() {
        return idProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

    public BigDecimal getSubtotal() {
        return precio.multiply(new BigDecimal(cantidad)).setScale(2, RoundingMode.HALF_UP);
    }
}

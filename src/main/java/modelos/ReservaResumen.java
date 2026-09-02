package modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReservaResumen {
    private final long idVenta;
    private final LocalDateTime fecha;
    private final LocalDateTime horaEntregaPactada;
    private final String numero;
    private final String direccion;
    private final String distrito;
    private final BigDecimal total;
    private final String estado;

    public ReservaResumen(long idVenta, LocalDateTime fecha, LocalDateTime horaEntregaPactada,
            String numero, String direccion, String distrito,
            BigDecimal total, String estado) {
        this.idVenta = idVenta;
        this.fecha = fecha;
        this.horaEntregaPactada = horaEntregaPactada;
        this.numero = numero;
        this.direccion = direccion;
        this.distrito = distrito;
        this.total = total;
        this.estado = VentaEstado.normalizar(estado);
    }

    public long getIdVenta() {
        return idVenta;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public LocalDateTime getHoraEntregaPactada() {
        return horaEntregaPactada;
    }

    public String getNumero() {
        return numero;
    }

    public String getDireccion() {
        return direccion;
    }

    public String getDistrito() {
        return distrito;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getEstado() {
        return estado;
    }
}

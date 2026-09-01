package modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReservaResumen {
    private final long idVenta;
    private final LocalDateTime fecha;
    private final LocalDateTime horaEntregaPactada;
    private final String clienteNombre;
    private final String clienteNumero;
    private final String clienteDireccion;
    private final String clienteDistrito;
    private final BigDecimal total;
    private final String estado;

    public ReservaResumen(long idVenta, LocalDateTime fecha, LocalDateTime horaEntregaPactada,
            String clienteNombre, String clienteNumero, String clienteDireccion, String clienteDistrito,
            BigDecimal total, String estado) {
        this.idVenta = idVenta;
        this.fecha = fecha;
        this.horaEntregaPactada = horaEntregaPactada;
        this.clienteNombre = clienteNombre;
        this.clienteNumero = clienteNumero;
        this.clienteDireccion = clienteDireccion;
        this.clienteDistrito = clienteDistrito;
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

    public String getClienteNombre() {
        return clienteNombre;
    }

    public String getClienteNumero() {
        return clienteNumero;
    }

    public String getClienteDireccion() {
        return clienteDireccion;
    }

    public String getClienteDistrito() {
        return clienteDistrito;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getEstado() {
        return estado;
    }
}

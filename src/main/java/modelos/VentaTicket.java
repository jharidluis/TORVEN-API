package modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VentaTicket {
    private final long idVenta;
    private final Cliente cliente;
    private final String documentoComprobante;
    private final LocalDateTime fecha;
    private final BigDecimal total;
    private final List<LineaVenta> lineas;
    private final String estado;

    public VentaTicket(long idVenta, Cliente cliente, String documentoComprobante,
            LocalDateTime fecha, BigDecimal total, List<LineaVenta> lineas) {
        this(idVenta, cliente, documentoComprobante, fecha, total, lineas, VentaEstado.VENDIDA);
    }

    public VentaTicket(long idVenta, Cliente cliente, String documentoComprobante,
            LocalDateTime fecha, BigDecimal total, List<LineaVenta> lineas, String estado) {
        this.idVenta = idVenta;
        this.cliente = cliente;
        this.documentoComprobante = documentoComprobante;
        this.fecha = fecha;
        this.total = total;
        this.lineas = Collections.unmodifiableList(new ArrayList<LineaVenta>(lineas));
        this.estado = VentaEstado.normalizar(estado);
    }

    public long getIdVenta() {
        return idVenta;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public String getDocumentoComprobante() {
        return documentoComprobante;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<LineaVenta> getLineas() {
        return lineas;
    }

    public String getEstado() {
        return estado;
    }
}

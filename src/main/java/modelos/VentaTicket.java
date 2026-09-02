package modelos;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class VentaTicket {
    private final long idVenta;
    private final LugarEntrega lugarEntrega;
    private final String documentoComprobante;
    private final LocalDateTime fecha;
    private final BigDecimal total;
    private final List<LineaVenta> lineas;
    private final String estado;
    private final LocalDateTime horaEntregaPactada;

    public VentaTicket(long idVenta, LugarEntrega lugarEntrega, String documentoComprobante,
            LocalDateTime fecha, BigDecimal total, List<LineaVenta> lineas) {
        this(idVenta, lugarEntrega, documentoComprobante, fecha, total, lineas, VentaEstado.VENDIDA, null);
    }

    public VentaTicket(long idVenta, LugarEntrega lugarEntrega, String documentoComprobante,
            LocalDateTime fecha, BigDecimal total, List<LineaVenta> lineas, String estado) {
        this(idVenta, lugarEntrega, documentoComprobante, fecha, total, lineas, estado, null);
    }

    public VentaTicket(long idVenta, LugarEntrega lugarEntrega, String documentoComprobante,
            LocalDateTime fecha, BigDecimal total, List<LineaVenta> lineas, String estado,
            LocalDateTime horaEntregaPactada) {
        this.idVenta = idVenta;
        this.lugarEntrega = lugarEntrega;
        this.documentoComprobante = documentoComprobante;
        this.fecha = fecha;
        this.total = total;
        this.lineas = Collections.unmodifiableList(new ArrayList<LineaVenta>(lineas));
        this.estado = VentaEstado.normalizar(estado);
        this.horaEntregaPactada = horaEntregaPactada;
    }

    public long getIdVenta() {
        return idVenta;
    }

    public LugarEntrega getLugarEntrega() {
        return lugarEntrega;
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

    public LocalDateTime getHoraEntregaPactada() {
        return horaEntregaPactada;
    }
}

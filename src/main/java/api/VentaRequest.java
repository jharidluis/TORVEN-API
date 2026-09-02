package api;

import java.util.List;

public class VentaRequest {
    public int idLugarEntrega;
    public List<LineaRequest> lineas;
    public String horaEntregaPactada;

    public static class LineaRequest {
        public int idProducto;
        public int cantidad;
    }
}

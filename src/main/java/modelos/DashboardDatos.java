package modelos;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DashboardDatos {
    private final BigDecimal ventasHoy;
    private final BigDecimal ventasSemana;
    private final BigDecimal ventasMes;
    private final int totalClientes;
    private final int totalProductos;
    private final int stockBajo;
    private final List<Object[]> distritos;
    private final List<Object[]> ventas;

    public DashboardDatos(BigDecimal ventasHoy, BigDecimal ventasSemana, BigDecimal ventasMes,
            int totalClientes, int totalProductos, int stockBajo,
            List<Object[]> distritos, List<Object[]> ventas) {
        this.ventasHoy = valor(ventasHoy);
        this.ventasSemana = valor(ventasSemana);
        this.ventasMes = valor(ventasMes);
        this.totalClientes = totalClientes;
        this.totalProductos = totalProductos;
        this.stockBajo = stockBajo;
        this.distritos = copia(distritos);
        this.ventas = copia(ventas);
    }

    public BigDecimal getVentasHoy() {
        return ventasHoy;
    }

    public BigDecimal getVentasSemana() {
        return ventasSemana;
    }

    public BigDecimal getVentasMes() {
        return ventasMes;
    }

    public int getTotalClientes() {
        return totalClientes;
    }

    public int getTotalProductos() {
        return totalProductos;
    }

    public int getStockBajo() {
        return stockBajo;
    }

    public List<Object[]> getDistritos() {
        return distritos;
    }

    public List<Object[]> getVentas() {
        return ventas;
    }

    private static BigDecimal valor(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static List<Object[]> copia(List<Object[]> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<Object[]> copia = new ArrayList<Object[]>(values.size());
        for (Object[] value : values) {
            copia.add(value == null ? null : value.clone());
        }
        return Collections.unmodifiableList(copia);
    }
}

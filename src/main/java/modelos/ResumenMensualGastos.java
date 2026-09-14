package modelos;

import java.math.BigDecimal;
import java.util.List;

public class ResumenMensualGastos {
    private final BigDecimal total;
    private final List<TotalCategoriaGasto> porCategoria;

    public ResumenMensualGastos(BigDecimal total, List<TotalCategoriaGasto> porCategoria) {
        this.total = total;
        this.porCategoria = porCategoria;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public List<TotalCategoriaGasto> getPorCategoria() {
        return porCategoria;
    }
}

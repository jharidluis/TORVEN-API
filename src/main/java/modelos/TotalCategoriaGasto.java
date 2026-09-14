package modelos;

import java.math.BigDecimal;

public class TotalCategoriaGasto {
    private final String categoria;
    private final BigDecimal total;

    public TotalCategoriaGasto(String categoria, BigDecimal total) {
        this.categoria = categoria;
        this.total = total;
    }

    public String getCategoria() {
        return categoria;
    }

    public BigDecimal getTotal() {
        return total;
    }
}

package vistas;

import dao.DashboardDAO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import modelos.VentaEstado;
import modelos.VentaTicket;
import modelos.DashboardDatos;
import servicios.VoucherService;

public class DashboardPanel extends JPanel {
    private static final Locale LOCALE_ES = new Locale("es", "PE");
    private static final DateTimeFormatter FECHA_BOTON = DateTimeFormatter.ofPattern("dd MMM yyyy", LOCALE_ES);
    private final DashboardDAO dao = new DashboardDAO();
    private final VoucherService voucherService = new VoucherService();
    private final JLabel lblVentasHoy = tarjetaValor("S/. 0.00");
    private final JLabel lblVentasSemana = tarjetaValor("S/. 0.00");
    private final JLabel lblVentasMes = tarjetaValor("S/. 0.00");
    private final JLabel lblClientes = tarjetaValor("0");
    private final JLabel lblProductos = tarjetaValor("0");
    private final JLabel lblStockBajo = tarjetaValor("0");
    private final DistritosBarChart graficaDistritos = new DistritosBarChart();
    private final DefaultTableModel modelo = Ui.modelo("Venta", "Fecha", "Cliente", "DNI/RUC", "Total", "Estado", "EstadoCodigo");
    private final JTable tabla = new JTable(modelo);
    private final FechaSelector fechaDesde = new FechaSelector("Desde");
    private final FechaSelector fechaHasta = new FechaSelector("Hasta");
    private final JLabel lblRangoDetalle = new JLabel("Ventas vendidas por fecha");
    private final JButton btnCanceladas = new JButton("Canceladas");
    private final JButton btnExportar = new JButton("Exportar venta PDF");
    private final JButton btnAbrir = new JButton("Abrir PDF");
    private boolean mostrarCanceladas;
    private SwingWorker<DashboardDatos, Void> cargaActual;
    private int versionCarga;

    public DashboardPanel() {
        setLayout(new BorderLayout(12, 12));
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setBackground(Ui.COLOR_FONDO);
        construir();
        configurarFechasIniciales();
        DatosEventBus.alCambiarClientes(new Runnable() {
            @Override
            public void run() {
                cargar();
            }
        });
        DatosEventBus.alCambiarProductos(new Runnable() {
            @Override
            public void run() {
                cargar();
            }
        });
        DatosEventBus.alCambiarVentas(new Runnable() {
            @Override
            public void run() {
                cargar();
            }
        });
    }

    public void cargar() {
        final LocalDate desde = fechaDesde.getFecha();
        final LocalDate hasta = fechaHasta.getFecha();
        final boolean canceladas = mostrarCanceladas;
        if (desde.isAfter(hasta)) {
            Ui.error(this, new SQLException("La fecha Desde no puede ser mayor que la fecha Hasta."));
            return;
        }

        final int version = ++versionCarga;
        if (cargaActual != null && !cargaActual.isDone()) {
            cargaActual.cancel(true);
        }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        cargaActual = new SwingWorker<DashboardDatos, Void>() {
            @Override
            protected DashboardDatos doInBackground() throws Exception {
                return dao.cargar(desde, hasta, canceladas);
            }

            @Override
            protected void done() {
                if (version != versionCarga) {
                    return;
                }
                try {
                    aplicarDatos(get());
                } catch (CancellationException ignored) {
                    // Una carga mas reciente reemplazo esta consulta.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Ui.error(DashboardPanel.this, excepcionReal(ex));
                } finally {
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        cargaActual.execute();
    }

    private void construir() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JLabel titulo = new JLabel("Dashboard");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 24f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        JLabel subtitulo = new JLabel("Resumen rapido para controlar la tienda");
        subtitulo.setForeground(Ui.COLOR_MUTED);
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(titulo);
        textos.add(subtitulo);
        JButton actualizar = new JButton("Actualizar");
        Ui.estilizarBotonSecundario(actualizar);
        Ui.estilizarBotonPrimario(btnExportar);
        Ui.estilizarBotonSecundario(btnAbrir);
        btnExportar.setEnabled(false);
        btnAbrir.setEnabled(false);
        actualizar.addActionListener(e -> cargar());

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);
        acciones.add(btnExportar);
        acciones.add(btnAbrir);
        acciones.add(actualizar);
        encabezado.add(textos, BorderLayout.WEST);
        encabezado.add(acciones, BorderLayout.EAST);

        JPanel tarjetas = new JPanel(new GridLayout(2, 3, 10, 10));
        tarjetas.setOpaque(false);
        tarjetas.add(tarjeta("Ventas de hoy", lblVentasHoy, Ui.COLOR_PRINCIPAL));
        tarjetas.add(tarjeta("Ventas semanales", lblVentasSemana, Ui.COLOR_ACCENTO));
        tarjetas.add(tarjeta("Ventas mensuales", lblVentasMes, Ui.COLOR_MORADO));
        tarjetas.add(tarjeta("Clientes", lblClientes, Ui.COLOR_VERDE));
        tarjetas.add(tarjeta("Productos", lblProductos, Ui.COLOR_PRINCIPAL));
        tarjetas.add(tarjeta("Stock bajo", lblStockBajo, Ui.COLOR_ALERTA));

        JPanel norte = new JPanel(new BorderLayout(0, 14));
        norte.setOpaque(false);
        norte.add(encabezado, BorderLayout.NORTH);
        norte.add(tarjetas, BorderLayout.CENTER);

        Ui.prepararTabla(tabla);
        Ui.ocultarColumna(tabla, 6);
        Ui.anchoColumna(tabla, 0, 72);
        Ui.anchoColumna(tabla, 1, 140);
        Ui.anchoColumna(tabla, 2, 210);
        Ui.anchoColumna(tabla, 3, 95);
        Ui.anchoColumna(tabla, 4, 95);
        Ui.anchoColumna(tabla, 5, 100);
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                actualizarAccionesVenta();
            }
        });
        JPanel ultimas = new JPanel(new BorderLayout(8, 8));
        ultimas.setOpaque(false);
        JPanel filtroVentas = new JPanel(new BorderLayout(10, 0));
        filtroVentas.setOpaque(false);
        JPanel textoFiltro = new JPanel(new GridLayout(2, 1));
        textoFiltro.setOpaque(false);
        JLabel rangoTitulo = new JLabel("Ventas por rango");
        rangoTitulo.setFont(rangoTitulo.getFont().deriveFont(Font.BOLD, 13f));
        rangoTitulo.setForeground(Ui.COLOR_TEXTO);
        JButton btnFiltrarVentas = new JButton("Filtrar");
        JButton btnHoyVentas = new JButton("Hoy");
        Ui.estilizarBotonPrimario(btnFiltrarVentas);
        Ui.estilizarBotonSecundario(btnHoyVentas);
        Ui.estilizarBotonSecundario(btnCanceladas);
        textoFiltro.add(rangoTitulo);
        lblRangoDetalle.setForeground(Ui.COLOR_MUTED);
        textoFiltro.add(lblRangoDetalle);

        JPanel controlesFecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controlesFecha.setOpaque(false);
        JLabel separadorFechas = new JLabel("a");
        separadorFechas.setForeground(Ui.COLOR_MUTED);
        controlesFecha.add(fechaDesde);
        controlesFecha.add(separadorFechas);
        controlesFecha.add(fechaHasta);
        controlesFecha.add(btnFiltrarVentas);
        controlesFecha.add(btnHoyVentas);
        controlesFecha.add(btnCanceladas);
        filtroVentas.add(textoFiltro, BorderLayout.WEST);
        filtroVentas.add(controlesFecha, BorderLayout.EAST);

        ultimas.add(filtroVentas, BorderLayout.NORTH);
        ultimas.add(new JScrollPane(tabla), BorderLayout.CENTER);

        JPanel grafica = new JPanel(new BorderLayout(8, 8));
        grafica.setOpaque(false);
        Ui.tarjeta(grafica);
        JLabel tituloGrafica = new JLabel("Distritos con mas ventas");
        tituloGrafica.setFont(tituloGrafica.getFont().deriveFont(Font.BOLD, 15f));
        tituloGrafica.setForeground(Ui.COLOR_TEXTO);
        grafica.add(tituloGrafica, BorderLayout.NORTH);
        grafica.add(graficaDistritos, BorderLayout.CENTER);

        JPanel centro = new JPanel(new GridLayout(2, 1, 0, 12));
        centro.setOpaque(false);
        centro.add(grafica);
        centro.add(ultimas);

        add(norte, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);

        btnExportar.addActionListener(e -> exportarVentaSeleccionada(true));
        btnAbrir.addActionListener(e -> exportarVentaSeleccionada(false));
        btnFiltrarVentas.addActionListener(e -> cargarVentasFiltradas());
        btnHoyVentas.addActionListener(e -> {
            seleccionarHoy();
            cargarVentasFiltradas();
        });
        btnCanceladas.addActionListener(e -> {
            mostrarCanceladas = !mostrarCanceladas;
            actualizarModoTablaVentas();
            cargarVentasFiltradas();
        });
        actualizarModoTablaVentas();
    }

    private void cargarVentasFiltradas() {
        cargar();
    }

    private void aplicarDatos(DashboardDatos datos) {
        lblVentasHoy.setText("S/. " + Ui.dinero(datos.getVentasHoy()));
        lblVentasSemana.setText("S/. " + Ui.dinero(datos.getVentasSemana()));
        lblVentasMes.setText("S/. " + Ui.dinero(datos.getVentasMes()));
        lblClientes.setText(String.valueOf(datos.getTotalClientes()));
        lblProductos.setText(String.valueOf(datos.getTotalProductos()));
        lblStockBajo.setText(String.valueOf(datos.getStockBajo()));
        graficaDistritos.setDatos(datos.getDistritos());

        modelo.setRowCount(0);
        for (Object[] venta : datos.getVentas()) {
            BigDecimal total = (BigDecimal) venta[4];
            String estado = VentaEstado.normalizar(String.valueOf(venta[5]));
            modelo.addRow(new Object[]{
                venta[0],
                venta[1],
                venta[2],
                venta[3] == null ? "" : venta[3],
                "S/. " + Ui.dinero(total),
                VentaEstado.etiqueta(estado),
                estado
            });
        }
        actualizarAccionesVenta();
    }

    private Exception excepcionReal(ExecutionException ex) {
        Throwable causa = ex.getCause();
        return causa instanceof Exception
                ? (Exception) causa
                : new SQLException("No se pudo actualizar el Dashboard.", causa);
    }

    private void configurarFechasIniciales() {
        seleccionarHoy();
    }

    private void seleccionarHoy() {
        LocalDate hoy = LocalDate.now();
        seleccionarRango(hoy, hoy);
    }

    private void seleccionarRango(LocalDate desde, LocalDate hasta) {
        fechaDesde.setFecha(desde);
        fechaHasta.setFecha(hasta);
    }

    private void actualizarModoTablaVentas() {
        if (mostrarCanceladas) {
            lblRangoDetalle.setText("Mostrando ventas canceladas por fecha");
            btnCanceladas.setBackground(Ui.COLOR_ALERTA);
            btnCanceladas.setForeground(Color.WHITE);
        } else {
            lblRangoDetalle.setText("Ventas vendidas por fecha");
            btnCanceladas.setBackground(Color.WHITE);
            btnCanceladas.setForeground(Ui.COLOR_PRINCIPAL);
        }
    }

    private void exportarVentaSeleccionada(boolean elegirRuta) {
        try {
            long idVenta = ventaSeleccionada();
            VentaTicket ticket = dao.obtenerVentaTicket(idVenta);
            Path path;
            if (elegirRuta) {
                JFileChooser chooser = new JFileChooser();
                chooser.setSelectedFile(voucherService.rutaSugerida(ticket).toFile());
                int result = chooser.showSaveDialog(this);
                if (result != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                path = chooser.getSelectedFile().toPath();
            } else {
                path = voucherService.rutaSugerida(ticket);
            }
            if (!path.toString().toLowerCase().endsWith(".pdf")) {
                path = path.resolveSibling(path.getFileName().toString() + ".pdf");
            }
            voucherService.exportarPdf(path, ticket);
            voucherService.abrirArchivo(path);
            Ui.info(this, "Voucher exportado.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private long ventaSeleccionada() throws SQLException {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            throw new SQLException("Selecciona una venta del dashboard.");
        }
        int modelRow = tabla.convertRowIndexToModel(row);
        Object value = modelo.getValueAt(modelRow, 0);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void actualizarAccionesVenta() {
        boolean tieneVenta = tabla.getSelectedRow() >= 0;
        btnExportar.setEnabled(tieneVenta);
        btnAbrir.setEnabled(tieneVenta);
    }

    private JPanel tarjeta(String titulo, JLabel valor, Color color) {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 224, 232)),
                BorderFactory.createEmptyBorder(14, 14, 14, 14)));
        panel.setBackground(Color.WHITE);
        JLabel label = new JLabel(titulo);
        label.setForeground(Ui.COLOR_MUTED);
        valor.setForeground(color);
        panel.add(label, BorderLayout.NORTH);
        panel.add(valor, BorderLayout.CENTER);
        return panel;
    }

    private JLabel tarjetaValor(String valor) {
        JLabel label = new JLabel(valor, SwingConstants.LEFT);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 22f));
        return label;
    }

    private static class FechaSelector extends JPanel {
        private static final String[] DIAS = {"Lun", "Mar", "Mie", "Jue", "Vie", "Sab", "Dom"};
        private final String etiqueta;
        private final JButton boton = new JButton();
        private LocalDate fecha = LocalDate.now();
        private YearMonth mesVisible = YearMonth.from(fecha);
        private JPopupMenu popup;

        FechaSelector(String etiqueta) {
            this.etiqueta = etiqueta;
            setLayout(new BorderLayout());
            setOpaque(false);
            prepararBotonPrincipal();
            add(boton, BorderLayout.CENTER);
            actualizarTexto();
        }

        LocalDate getFecha() {
            return fecha;
        }

        void setFecha(LocalDate nuevaFecha) {
            if (nuevaFecha == null) {
                return;
            }
            fecha = nuevaFecha;
            mesVisible = YearMonth.from(fecha);
            actualizarTexto();
        }

        private void prepararBotonPrincipal() {
            boton.setFocusPainted(false);
            boton.setOpaque(true);
            boton.setBackground(Color.WHITE);
            boton.setForeground(Ui.COLOR_TEXTO);
            boton.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(194, 207, 220)),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)));
            boton.setPreferredSize(new Dimension(166, 36));
            boton.setHorizontalAlignment(SwingConstants.LEFT);
            boton.addActionListener(e -> mostrarPopup());
        }

        private void mostrarPopup() {
            if (popup != null && popup.isVisible()) {
                popup.setVisible(false);
                return;
            }
            abrirPopup();
        }

        private void abrirPopup() {
            popup = new JPopupMenu();
            popup.setBorder(BorderFactory.createLineBorder(new Color(194, 207, 220)));
            popup.add(crearCalendario());
            popup.show(boton, 0, boton.getHeight() + 2);
        }

        private JPanel crearCalendario() {
            JPanel panel = new JPanel(new BorderLayout(0, 8));
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JPanel cabecera = new JPanel(new BorderLayout(8, 0));
            cabecera.setOpaque(false);
            JButton anterior = botonNavegacion("<");
            JButton siguiente = botonNavegacion(">");
            JLabel tituloMes = new JLabel(nombreMes(), SwingConstants.CENTER);
            tituloMes.setForeground(Ui.COLOR_TEXTO);
            tituloMes.setFont(tituloMes.getFont().deriveFont(Font.BOLD, 13f));
            anterior.addActionListener(e -> cambiarMes(-1));
            siguiente.addActionListener(e -> cambiarMes(1));
            cabecera.add(anterior, BorderLayout.WEST);
            cabecera.add(tituloMes, BorderLayout.CENTER);
            cabecera.add(siguiente, BorderLayout.EAST);

            JPanel grilla = new JPanel(new GridLayout(0, 7, 4, 4));
            grilla.setOpaque(false);
            for (String dia : DIAS) {
                JLabel labelDia = new JLabel(dia, SwingConstants.CENTER);
                labelDia.setForeground(Ui.COLOR_MUTED);
                labelDia.setFont(labelDia.getFont().deriveFont(Font.BOLD, 11f));
                grilla.add(labelDia);
            }

            int espaciosInicio = mesVisible.atDay(1).getDayOfWeek().getValue() - 1;
            int celdas = 0;
            for (int i = 0; i < espaciosInicio; i++) {
                grilla.add(celdaVacia());
                celdas++;
            }
            for (int dia = 1; dia <= mesVisible.lengthOfMonth(); dia++) {
                grilla.add(botonDia(dia));
                celdas++;
            }
            while (celdas < 42) {
                grilla.add(celdaVacia());
                celdas++;
            }

            panel.add(cabecera, BorderLayout.NORTH);
            panel.add(grilla, BorderLayout.CENTER);
            return panel;
        }

        private JButton botonDia(int dia) {
            final LocalDate diaFecha = mesVisible.atDay(dia);
            JButton dayButton = new JButton(String.valueOf(dia));
            dayButton.setFocusPainted(false);
            dayButton.setOpaque(true);
            dayButton.setMargin(new Insets(4, 4, 4, 4));
            dayButton.setPreferredSize(new Dimension(34, 28));
            boolean seleccionado = diaFecha.equals(fecha);
            if (seleccionado) {
                dayButton.setBackground(Ui.COLOR_PRINCIPAL);
                dayButton.setForeground(Color.WHITE);
                dayButton.setBorder(BorderFactory.createLineBorder(Ui.COLOR_PRINCIPAL));
            } else if (diaFecha.equals(LocalDate.now())) {
                dayButton.setBackground(new Color(236, 242, 247));
                dayButton.setForeground(Ui.COLOR_PRINCIPAL);
                dayButton.setBorder(BorderFactory.createLineBorder(new Color(194, 207, 220)));
            } else {
                dayButton.setBackground(Color.WHITE);
                dayButton.setForeground(Ui.COLOR_TEXTO);
                dayButton.setBorder(BorderFactory.createLineBorder(new Color(229, 231, 235)));
            }
            dayButton.addActionListener(e -> {
                setFecha(diaFecha);
                if (popup != null) {
                    popup.setVisible(false);
                }
            });
            return dayButton;
        }

        private JLabel celdaVacia() {
            JLabel label = new JLabel("");
            label.setPreferredSize(new Dimension(34, 28));
            return label;
        }

        private JButton botonNavegacion(String texto) {
            JButton button = new JButton(texto);
            button.setFocusPainted(false);
            button.setMargin(new Insets(2, 8, 2, 8));
            button.setBackground(Color.WHITE);
            button.setForeground(Ui.COLOR_PRINCIPAL);
            button.setBorder(BorderFactory.createLineBorder(new Color(194, 207, 220)));
            return button;
        }

        private void cambiarMes(int cantidad) {
            mesVisible = mesVisible.plusMonths(cantidad);
            if (popup != null && popup.isVisible()) {
                popup.setVisible(false);
                abrirPopup();
            }
        }

        private void actualizarTexto() {
            boton.setText(etiqueta + ": " + fecha.format(FECHA_BOTON).replace(".", "") + "  v");
        }

        private String nombreMes() {
            String mes = mesVisible.getMonth().getDisplayName(TextStyle.FULL, LOCALE_ES);
            return capitalizar(mes) + " " + mesVisible.getYear();
        }

        private String capitalizar(String texto) {
            if (texto == null || texto.isEmpty()) {
                return "";
            }
            return texto.substring(0, 1).toUpperCase(LOCALE_ES) + texto.substring(1);
        }
    }

    private static class DistritosBarChart extends JPanel {
        private List<Object[]> datos = Collections.emptyList();

        DistritosBarChart() {
            setOpaque(false);
            setPreferredSize(new Dimension(520, 190));
        }

        void setDatos(List<Object[]> nuevosDatos) {
            datos = nuevosDatos == null
                    ? Collections.<Object[]>emptyList()
                    : new ArrayList<Object[]>(nuevosDatos);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int width = getWidth();
                int height = getHeight();
                if (datos.isEmpty()) {
                    g.setColor(Ui.COLOR_MUTED);
                    g.drawString("Aun no hay ventas asociadas a distritos.", 16, Math.max(28, height / 2));
                    return;
                }

                int left = Math.min(150, Math.max(95, width / 4));
                int right = 22;
                int top = 12;
                int bottom = 22;
                int chartWidth = Math.max(40, width - left - right);
                int rows = datos.size();
                int gap = 8;
                int availableHeight = Math.max(40, height - top - bottom);
                int barHeight = Math.max(14, (availableHeight - (rows - 1) * gap) / rows);

                double max = 0.0;
                for (Object[] fila : datos) {
                    BigDecimal total = (BigDecimal) fila[1];
                    max = Math.max(max, total == null ? 0.0 : total.doubleValue());
                }
                if (max <= 0.0) {
                    max = 1.0;
                }

                FontMetrics metrics = g.getFontMetrics();
                for (int i = 0; i < rows; i++) {
                    Object[] fila = datos.get(i);
                    String distrito = String.valueOf(fila[0]);
                    BigDecimal total = (BigDecimal) fila[1];
                    int ventas = ((Integer) fila[2]).intValue();
                    double value = total == null ? 0.0 : total.doubleValue();
                    int barWidth = Math.max(4, (int) Math.round((value / max) * chartWidth));
                    int y = top + i * (barHeight + gap);

                    g.setColor(Ui.COLOR_TEXTO);
                    g.drawString(recortar(distrito, metrics, left - 18), 8, y + barHeight - 3);

                    g.setColor(new Color(229, 231, 235));
                    g.fillRoundRect(left, y, chartWidth, barHeight, 6, 6);
                    g.setColor(i == 0 ? Ui.COLOR_PRINCIPAL : Ui.COLOR_ACCENTO);
                    g.fillRoundRect(left, y, barWidth, barHeight, 6, 6);

                    g.setColor(Ui.COLOR_TEXTO);
                    String etiqueta = "S/. " + Ui.dinero(total == null ? BigDecimal.ZERO : total)
                            + " | " + ventas + " ventas";
                    int labelX = left + Math.min(barWidth + 8, Math.max(8, chartWidth - metrics.stringWidth(etiqueta)));
                    g.drawString(etiqueta, labelX, y + barHeight - 3);
                }
            } finally {
                g.dispose();
            }
        }

        private String recortar(String value, FontMetrics metrics, int maxWidth) {
            String limpio = value == null ? "" : value.trim();
            if (metrics.stringWidth(limpio) <= maxWidth) {
                return limpio;
            }
            while (limpio.length() > 1 && metrics.stringWidth(limpio + ".") > maxWidth) {
                limpio = limpio.substring(0, limpio.length() - 1);
            }
            return limpio + ".";
        }
    }
}

package vistas;

import dao.LugarEntregaDAO;
import dao.ProductoDAO;
import dao.VentaDAO;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.print.PrinterException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.PlainDocument;
import javax.swing.table.DefaultTableModel;
import javax.swing.event.TableModelEvent;
import modelos.Distrito;
import modelos.LineaVenta;
import modelos.LugarEntrega;
import modelos.Producto;
import modelos.Usuario;
import modelos.VentaEstado;
import modelos.VentaTicket;
import servicios.VoucherService;

public class VentasPanel extends JPanel {
    private static final DateTimeFormatter FECHA_RESERVA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final LugarEntregaDAO lugarEntregaDAO = new LugarEntregaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final VentaDAO ventaDAO = new VentaDAO();
    private final VoucherService voucherService = new VoucherService();
    private final boolean mostrarGestionReservas;

    private final JTextField txtDireccionEntrega = new JTextField(22);
    private final JComboBox<Distrito> comboDistritoEntrega = new JComboBox<Distrito>();
    private final JTextField txtNumeroEntrega = new JTextField(14);
    private final JTextField txtBuscarProducto = new JTextField(22);
    private final JTable tablaProductos = new JTable();
    private final JTable tablaCarrito = new JTable();
    private final JTable tablaReservas = new JTable();
    private final DefaultTableModel modeloProductos = Ui.modelo("ID", "Producto", "Precio", "Stock");
    private final DefaultTableModel modeloReservas = Ui.modelo("Reserva", "Fecha", "Direccion", "Comprobante", "Total");
    private final DefaultTableModel modeloCarrito = new DefaultTableModel(
            new Object[]{"ID", "Producto", "Precio unit.", "Cant.", "Subtotal"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 3;
        }
    };
    private final JLabel lblTotal = new JLabel("Total: S/. 0.00");
    private final JButton btnPagar = new JButton("Reservar");
    private final JButton btnImprimir = new JButton("Imprimir");
    private final JButton btnExportar = new JButton("Exportar PDF");
    private final JButton btnVenderReserva = new JButton("Marcar vendido");
    private final JButton btnCancelarReserva = new JButton("Cancelar reserva");
    private final JLabel lblReservasPendientes = new JLabel("0 reservas en proceso");
    private final JLabel lblTotalReservado = new JLabel("Total reservado: S/. 0.00");

    private final List<LineaVenta> carrito = new ArrayList<LineaVenta>();
    private VentaTicket ultimoTicket;
    private boolean actualizandoCarrito;
    private boolean distritosCargados;
    private SwingWorker<List<Producto>, Void> cargaProductosActual;
    private SwingWorker<List<Object[]>, Void> cargaReservasActual;
    private int versionProductos;
    private int versionReservas;

    public VentasPanel() {
        this(null);
    }

    public VentasPanel(Usuario usuario) {
        this.mostrarGestionReservas = usuario != null && !usuario.esAdministrador();
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Ui.COLOR_FONDO);
        construir();
        Ui.alCambiarTexto(txtBuscarProducto, new Runnable() {
            @Override
            public void run() {
                cargarProductos();
            }
        });
        DatosEventBus.alCambiarProductos(new Runnable() {
            @Override
            public void run() {
                cargarProductos();
            }
        });
        if (mostrarGestionReservas) {
            DatosEventBus.alCambiarVentas(new Runnable() {
                @Override
                public void run() {
                    cargarReservasEnProceso();
                }
            });
        }
    }

    public void cargarDatos() {
        cargarDistritosSiHaceFalta();
        cargarProductos();
        if (mostrarGestionReservas) {
            cargarReservasEnProceso();
        }
    }

    private void construir() {
        tablaProductos.setModel(modeloProductos);
        tablaCarrito.setModel(modeloCarrito);
        tablaReservas.setModel(modeloReservas);
        tablaCarrito.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        Ui.prepararTabla(tablaProductos);
        Ui.prepararTabla(tablaCarrito);
        Ui.prepararTabla(tablaReservas);
        Ui.ocultarColumna(tablaProductos, 0);
        Ui.anchoColumna(tablaProductos, 1, 250);
        Ui.anchoColumna(tablaProductos, 2, 90);
        Ui.anchoColumna(tablaProductos, 3, 70);
        Ui.columnaDinero(tablaProductos, 2);
        Ui.columnaDerecha(tablaProductos, 3);
        Ui.anchoColumna(tablaCarrito, 1, 260);
        Ui.anchoColumna(tablaCarrito, 2, 90);
        Ui.anchoColumna(tablaCarrito, 3, 60);
        Ui.anchoColumna(tablaCarrito, 4, 95);
        Ui.columnaDinero(tablaCarrito, 2);
        Ui.columnaDerecha(tablaCarrito, 3);
        Ui.columnaDinero(tablaCarrito, 4);
        Ui.ocultarColumna(tablaCarrito, 0);
        Ui.anchoColumna(tablaReservas, 0, 80);
        Ui.anchoColumna(tablaReservas, 1, 125);
        Ui.anchoColumna(tablaReservas, 2, 260);
        Ui.anchoColumna(tablaReservas, 3, 100);
        Ui.anchoColumna(tablaReservas, 4, 95);
        Ui.columnaDinero(tablaReservas, 4);
        tablaCarrito.getColumnModel().getColumn(3).setCellEditor(new CantidadCellEditor());
        modeloCarrito.addTableModelListener(e -> {
            if (!actualizandoCarrito && e.getType() == TableModelEvent.UPDATE && e.getColumn() == 3) {
                actualizarCantidadDesdeTabla(e.getFirstRow());
            }
        });
        tablaReservas.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                actualizarAccionesReserva();
            }
        });

        tablaProductos.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    agregarProducto();
                }
            }
        });

        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setOpaque(false);
        centro.add(panelEntrega(), BorderLayout.NORTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panelProductos(), panelCarrito());
        split.setResizeWeight(0.60);
        split.setBorder(null);
        centro.add(split, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new BorderLayout());
        acciones.setOpaque(false);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        botones.setOpaque(false);
        JButton btnAgregar = new JButton("Agregar producto");
        JButton btnQuitar = new JButton("Quitar producto");
        JButton btnVaciar = new JButton("Vaciar");

        botones.add(btnAgregar);
        botones.add(btnQuitar);
        botones.add(btnVaciar);
        botones.add(btnPagar);
        botones.add(btnImprimir);
        botones.add(btnExportar);
        Ui.estilizarBotonSecundario(btnAgregar);
        Ui.estilizarBotonSecundario(btnQuitar);
        Ui.estilizarBotonSecundario(btnVaciar);
        Ui.estilizarBotonPrimario(btnPagar);
        Ui.estilizarBotonSecundario(btnImprimir);
        Ui.estilizarBotonSecundario(btnExportar);

        lblTotal.setFont(lblTotal.getFont().deriveFont(Font.BOLD, 18f));
        acciones.add(botones, BorderLayout.WEST);
        acciones.add(lblTotal, BorderLayout.EAST);

        JPanel nuevaReserva = new JPanel(new BorderLayout(0, 10));
        nuevaReserva.setOpaque(false);
        nuevaReserva.add(centro, BorderLayout.CENTER);
        nuevaReserva.add(acciones, BorderLayout.SOUTH);

        if (mostrarGestionReservas) {
            JTabbedPane tabsVentas = new JTabbedPane();
            tabsVentas.addTab("Nueva reserva", nuevaReserva);
            tabsVentas.addTab("Reservas en proceso", panelReservas());
            add(tabsVentas, BorderLayout.CENTER);
        } else {
            add(nuevaReserva, BorderLayout.CENTER);
        }

        btnAgregar.addActionListener(e -> agregarProducto());
        btnQuitar.addActionListener(e -> quitarProductoResumen());
        btnVaciar.addActionListener(e -> vaciarCarrito());
        btnPagar.addActionListener(e -> reservar());
        btnImprimir.addActionListener(e -> imprimirUltimo());
        btnExportar.addActionListener(e -> exportarUltimo());
        actualizarBotonesVoucher();
        actualizarAccionesReserva();
        actualizarCarrito();
    }

    private JPanel panelEntrega() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        Ui.tarjeta(panel);

        JLabel titulo = new JLabel("Entrega");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 15f));
        titulo.setForeground(Ui.COLOR_TEXTO);

        JPanel campos = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        campos.setOpaque(false);
        campos.add(new JLabel("Direccion"));
        campos.add(txtDireccionEntrega);
        campos.add(new JLabel("Distrito"));
        campos.add(comboDistritoEntrega);
        campos.add(new JLabel("Numero (opcional)"));
        campos.add(txtNumeroEntrega);

        panel.add(titulo, BorderLayout.NORTH);
        panel.add(campos, BorderLayout.CENTER);
        return panel;
    }

    private JPanel panelProductos() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        Ui.tarjeta(panel);
        JPanel encabezado = new JPanel(new BorderLayout(12, 0));
        encabezado.setOpaque(false);
        JLabel titulo = new JLabel("Productos");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 15f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        JPanel buscar = new JPanel(new BorderLayout(6, 0));
        buscar.setOpaque(false);
        buscar.add(new JLabel("Buscar"), BorderLayout.WEST);
        buscar.add(txtBuscarProducto, BorderLayout.CENTER);
        encabezado.add(titulo, BorderLayout.WEST);
        encabezado.add(buscar, BorderLayout.CENTER);
        panel.add(encabezado, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaProductos), BorderLayout.CENTER);
        return panel;
    }

    private JPanel panelCarrito() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        Ui.tarjeta(panel);
        JLabel titulo = new JLabel("Resumen de venta");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 15f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        panel.add(titulo, BorderLayout.NORTH);
        panel.add(new JScrollPane(tablaCarrito), BorderLayout.CENTER);
        return panel;
    }

    private JPanel panelReservas() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        JPanel encabezado = new JPanel(new BorderLayout(12, 0));
        encabezado.setOpaque(false);
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        JLabel titulo = new JLabel("Reservas en proceso");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 16f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        JLabel detalle = new JLabel("Confirma la venta o cancela la reserva cuando termines la visita");
        detalle.setForeground(Ui.COLOR_MUTED);
        textos.add(titulo);
        textos.add(detalle);

        JPanel resumen = new JPanel(new GridLayout(2, 1));
        resumen.setOpaque(false);
        lblReservasPendientes.setHorizontalAlignment(JLabel.RIGHT);
        lblReservasPendientes.setForeground(Ui.COLOR_TEXTO);
        lblReservasPendientes.setFont(lblReservasPendientes.getFont().deriveFont(Font.BOLD, 14f));
        lblTotalReservado.setHorizontalAlignment(JLabel.RIGHT);
        lblTotalReservado.setForeground(Ui.COLOR_MUTED);
        resumen.add(lblReservasPendientes);
        resumen.add(lblTotalReservado);
        encabezado.add(textos, BorderLayout.WEST);
        encabezado.add(resumen, BorderLayout.EAST);

        JPanel tablaPanel = new JPanel(new BorderLayout(8, 8));
        Ui.tarjeta(tablaPanel);
        tablaPanel.add(new JScrollPane(tablaReservas), BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);
        JButton btnActualizar = new JButton("Actualizar");
        Ui.estilizarBotonSecundario(btnActualizar);
        Ui.estilizarBotonPrimario(btnVenderReserva);
        Ui.estilizarBotonSecundario(btnCancelarReserva);
        acciones.add(btnActualizar);
        acciones.add(btnCancelarReserva);
        acciones.add(btnVenderReserva);

        btnActualizar.addActionListener(e -> cargarReservasEnProceso());
        btnVenderReserva.addActionListener(e -> cambiarEstadoReservaSeleccionada(VentaEstado.VENDIDA));
        btnCancelarReserva.addActionListener(e -> cambiarEstadoReservaSeleccionada(VentaEstado.CANCELADA));

        panel.add(encabezado, BorderLayout.NORTH);
        panel.add(tablaPanel, BorderLayout.CENTER);
        panel.add(acciones, BorderLayout.SOUTH);
        return panel;
    }

    private void cargarDistritosSiHaceFalta() {
        if (distritosCargados) {
            return;
        }
        distritosCargados = true;
        new SwingWorker<List<Distrito>, Void>() {
            @Override
            protected List<Distrito> doInBackground() throws Exception {
                return lugarEntregaDAO.listarDistritos();
            }

            @Override
            protected void done() {
                try {
                    for (Distrito distrito : get()) {
                        comboDistritoEntrega.addItem(distrito);
                        if ("Otro".equals(distrito.getNombre())) {
                            comboDistritoEntrega.setSelectedItem(distrito);
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Ui.error(VentasPanel.this, excepcionReal(ex));
                }
            }
        }.execute();
    }

    private void cargarProductos() {
        final String filtro = txtBuscarProducto.getText();
        final int version = ++versionProductos;
        if (cargaProductosActual != null && !cargaProductosActual.isDone()) {
            cargaProductosActual.cancel(true);
        }
        cargaProductosActual = new SwingWorker<List<Producto>, Void>() {
            @Override
            protected List<Producto> doInBackground() throws Exception {
                return productoDAO.listar(filtro);
            }

            @Override
            protected void done() {
                if (version != versionProductos) {
                    return;
                }
                try {
                    aplicarProductos(get());
                } catch (CancellationException ignored) {
                    // La busqueda mas reciente reemplazo esta consulta.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Ui.error(VentasPanel.this, excepcionReal(ex));
                }
            }
        };
        cargaProductosActual.execute();
    }

    private void cargarReservasEnProceso() {
        final int version = ++versionReservas;
        if (cargaReservasActual != null && !cargaReservasActual.isDone()) {
            cargaReservasActual.cancel(true);
        }
        cargaReservasActual = new SwingWorker<List<Object[]>, Void>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                return ventaDAO.listarReservasEnProceso();
            }

            @Override
            protected void done() {
                if (version != versionReservas) {
                    return;
                }
                try {
                    aplicarReservas(get());
                } catch (CancellationException ignored) {
                    // Una carga mas reciente reemplazo esta consulta.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Ui.error(VentasPanel.this, excepcionReal(ex));
                }
            }
        };
        cargaReservasActual.execute();
    }

    private void aplicarProductos(List<Producto> productos) {
        modeloProductos.setRowCount(0);
        for (Producto producto : productos) {
            modeloProductos.addRow(new Object[]{
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                producto.getStock()
            });
        }
    }

    private void aplicarReservas(List<Object[]> reservas) {
        modeloReservas.setRowCount(0);
        BigDecimal totalReservado = BigDecimal.ZERO;
        for (Object[] reserva : reservas) {
            BigDecimal total = (BigDecimal) reserva[4];
            totalReservado = totalReservado.add(total == null ? BigDecimal.ZERO : total);
            modeloReservas.addRow(new Object[]{
                reserva[0],
                fechaReserva(reserva[1]),
                reserva[2],
                reserva[3] == null ? "" : reserva[3],
                total
            });
        }
        String textoReservas = reservas.size() == 1 ? "1 reserva en proceso"
                : reservas.size() + " reservas en proceso";
        lblReservasPendientes.setText(textoReservas);
        lblTotalReservado.setText("Total reservado: S/. " + Ui.dinero(totalReservado));
        actualizarAccionesReserva();
    }

    private Exception excepcionReal(ExecutionException ex) {
        Throwable causa = ex.getCause();
        return causa instanceof Exception
                ? (Exception) causa
                : new SQLException("No se pudieron actualizar los datos de ventas.", causa);
    }

    private String fechaReserva(Object value) {
        if (value instanceof Timestamp) {
            return FECHA_RESERVA.format(((Timestamp) value).toLocalDateTime());
        }
        return value == null ? "" : String.valueOf(value);
    }

    private void agregarProducto() {
        int row = tablaProductos.getSelectedRow();
        if (row < 0) {
            Ui.aviso(this, "Selecciona un producto antes de agregarlo al resumen.");
            return;
        }
        int id = (Integer) modeloProductos.getValueAt(row, 0);
        String nombre = String.valueOf(modeloProductos.getValueAt(row, 1));
        BigDecimal precio = (BigDecimal) modeloProductos.getValueAt(row, 2);
        int stock = (Integer) modeloProductos.getValueAt(row, 3);

        if (stock <= 0) {
            Ui.aviso(this, "Este producto no tiene unidades disponibles en stock.");
            return;
        }

        int cantidad = 1;
        LineaVenta existente = buscarLinea(id);
        if (existente != null) {
            seleccionarLinea(id);
            Ui.aviso(this, "El producto ya está en el resumen. Puedes editar la cantidad directamente en la tabla.");
            return;
        } else {
            if (cantidad > stock) {
                Ui.aviso(this, "Solo hay " + stock + " unidades disponibles para este producto.");
                return;
            }
            carrito.add(new LineaVenta(id, nombre, precio, cantidad));
        }
        actualizarCarrito();
    }

    private void vaciarCarrito() {
        if (carrito.isEmpty()) {
            return;
        }
        if (Ui.confirmar(this, "Vaciar el carrito?")) {
            carrito.clear();
            actualizarCarrito();
        }
    }

    private void quitarProductoResumen() {
        int row = tablaCarrito.getSelectedRow();
        if (row < 0) {
            Ui.aviso(this, "Selecciona un producto del resumen antes de quitarlo.");
            return;
        }
        int id = ((Integer) modeloCarrito.getValueAt(row, 0)).intValue();
        LineaVenta linea = buscarLinea(id);
        if (linea != null) {
            carrito.remove(linea);
            actualizarCarrito();
        }
    }

    private void reservar() {
        try {
            String direccion = txtDireccionEntrega.getText().trim();
            Distrito distrito = (Distrito) comboDistritoEntrega.getSelectedItem();
            if (direccion.isEmpty() || distrito == null) {
                Ui.aviso(this, "Ingresa la direccion y el distrito de entrega antes de registrar la venta.");
                return;
            }
            LugarEntrega lugarEntrega = new LugarEntrega();
            lugarEntrega.setDireccion(direccion);
            lugarEntrega.setNumero(txtNumeroEntrega.getText().trim());
            lugarEntrega.setIdDistrito(distrito.getId());
            lugarEntregaDAO.crear(lugarEntrega);

            List<LineaVenta> copia = new ArrayList<LineaVenta>();
            for (LineaVenta linea : carrito) {
                copia.add(new LineaVenta(linea.getIdProducto(), linea.getNombreProducto(),
                        linea.getPrecio(), linea.getCantidad()));
            }
            VentaTicket ticket = ventaDAO.registrarReserva(lugarEntrega.getId(), copia);
            ultimoTicket = ticket;
            carrito.clear();
            limpiarFormularioEntrega();
            actualizarCarrito();
            DatosEventBus.publicarVentas();
            DatosEventBus.publicarProductos();
            actualizarBotonesVoucher();
            mostrarVoucher(ticket);
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void cambiarEstadoReservaSeleccionada(String estadoNuevo) {
        try {
            long idReserva = reservaSeleccionada();
            String accion = VentaEstado.VENDIDA.equals(estadoNuevo)
                    ? "marcar esta reserva como vendida?"
                    : "cancelar esta reserva y devolver el stock?";
            if (!Ui.confirmar(this, "Deseas " + accion)) {
                return;
            }
            ventaDAO.cambiarEstadoReserva(idReserva, estadoNuevo);
            DatosEventBus.publicarVentas();
            DatosEventBus.publicarProductos();
            Ui.info(this, "Reserva actualizada.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private long reservaSeleccionada() throws SQLException {
        int row = tablaReservas.getSelectedRow();
        if (row < 0) {
            throw new SQLException("Selecciona una reserva en proceso.");
        }
        int modelRow = tablaReservas.convertRowIndexToModel(row);
        Object value = modeloReservas.getValueAt(modelRow, 0);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private void mostrarVoucher(final VentaTicket ticket) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = new JDialog(owner, "Voucher", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout(8, 8));
        JTextArea area = new JTextArea(voucherService.crearTexto(ticket), 24, 52);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton imprimir = new JButton("Imprimir");
        JButton exportar = new JButton("Exportar PDF");
        JButton cerrar = new JButton("Cerrar");
        Ui.estilizarBotonPrimario(imprimir);
        Ui.estilizarBotonSecundario(exportar);
        Ui.estilizarBotonSecundario(cerrar);
        botones.add(imprimir);
        botones.add(exportar);
        botones.add(cerrar);

        imprimir.addActionListener(e -> imprimirUltimo());
        exportar.addActionListener(e -> exportarUltimo());
        cerrar.addActionListener(e -> dialog.dispose());

        dialog.add(new JScrollPane(area), BorderLayout.CENTER);
        dialog.add(botones, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void imprimirUltimo() {
        if (ultimoTicket == null) {
            Ui.aviso(this, "Primero registra una venta para poder imprimir su voucher.");
            return;
        }
        try {
            voucherService.imprimir(ultimoTicket);
        } catch (PrinterException ex) {
            Ui.error(this, ex);
        }
    }

    private void exportarUltimo() {
        if (ultimoTicket == null) {
            Ui.aviso(this, "Primero registra una venta para poder exportar su voucher.");
            return;
        }
        try {
            JFileChooser chooser = new JFileChooser();
            chooser.setSelectedFile(voucherService.rutaSugerida(ultimoTicket).toFile());
            int result = chooser.showSaveDialog(this);
            if (result != JFileChooser.APPROVE_OPTION) {
                return;
            }
            Path path = chooser.getSelectedFile().toPath();
            if (!path.toString().toLowerCase().endsWith(".pdf")) {
                path = path.resolveSibling(path.getFileName().toString() + ".pdf");
            }
            voucherService.exportarPdf(path, ultimoTicket);
            voucherService.abrirArchivo(path);
            Ui.info(this, "Voucher exportado.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void actualizarCarrito() {
        actualizandoCarrito = true;
        try {
            modeloCarrito.setRowCount(0);
            for (LineaVenta linea : carrito) {
                modeloCarrito.addRow(new Object[]{
                    linea.getIdProducto(),
                    linea.getNombreProducto(),
                    linea.getPrecio(),
                    linea.getCantidad(),
                    linea.getSubtotal()
                });
            }
        } finally {
            actualizandoCarrito = false;
        }
        lblTotal.setText("Total: S/. " + Ui.dinero(totalCarrito()));
        btnPagar.setEnabled(!carrito.isEmpty());
    }

    private void actualizarCantidadDesdeTabla(int row) {
        if (row < 0 || row >= modeloCarrito.getRowCount()) {
            return;
        }
        int idProducto = ((Integer) modeloCarrito.getValueAt(row, 0)).intValue();
        LineaVenta linea = buscarLinea(idProducto);
        if (linea == null) {
            actualizarCarrito();
            return;
        }

        try {
            int cantidad = leerCantidad(modeloCarrito.getValueAt(row, 3));
            int stock = stockActual(idProducto);
            if (cantidad > stock) {
                throw new SQLException("Solo hay " + stock + " unidades disponibles.");
            }
            linea.setCantidad(cantidad);
            modeloCarrito.setValueAt(linea.getSubtotal(), row, 4);
            lblTotal.setText("Total: S/. " + Ui.dinero(totalCarrito()));
            seleccionarLinea(idProducto);
        } catch (Exception ex) {
            Ui.error(this, ex);
            actualizarCarrito();
            seleccionarLinea(idProducto);
        }
    }

    private void actualizarCantidadMientrasEdita(String texto) {
        if (actualizandoCarrito || texto == null || !texto.matches("[0-9]+")) {
            return;
        }
        int row = tablaCarrito.getEditingRow();
        if (row < 0) {
            return;
        }
        int modelRow = tablaCarrito.convertRowIndexToModel(row);
        if (modelRow < 0 || modelRow >= modeloCarrito.getRowCount()) {
            return;
        }
        try {
            int cantidad = Integer.parseInt(texto);
            if (cantidad <= 0) {
                return;
            }
            int idProducto = ((Integer) modeloCarrito.getValueAt(modelRow, 0)).intValue();
            if (cantidad > stockActual(idProducto)) {
                return;
            }
            LineaVenta linea = buscarLinea(idProducto);
            if (linea == null) {
                return;
            }
            linea.setCantidad(cantidad);
            actualizandoCarrito = true;
            try {
                modeloCarrito.setValueAt(linea.getSubtotal(), modelRow, 4);
            } finally {
                actualizandoCarrito = false;
            }
            lblTotal.setText("Total: S/. " + Ui.dinero(totalCarrito()));
        } catch (Exception ignored) {
            // La validacion completa se muestra al confirmar la celda.
        }
    }

    private int leerCantidad(Object value) throws SQLException {
        String texto = value == null ? "" : String.valueOf(value).trim();
        if (!texto.matches("[0-9]+")) {
            throw new SQLException("La cantidad debe ser un numero entero.");
        }
        int cantidad;
        try {
            cantidad = Integer.parseInt(texto);
        } catch (NumberFormatException ex) {
            throw new SQLException("La cantidad debe ser un numero entero.", ex);
        }
        if (cantidad <= 0) {
            throw new SQLException("La cantidad debe ser mayor a cero.");
        }
        return cantidad;
    }

    private int stockActual(int idProducto) throws SQLException {
        for (int i = 0; i < modeloProductos.getRowCount(); i++) {
            if (((Integer) modeloProductos.getValueAt(i, 0)).intValue() == idProducto) {
                return ((Integer) modeloProductos.getValueAt(i, 3)).intValue();
            }
        }
        Producto producto = productoDAO.obtenerPorId(idProducto);
        if (producto == null) {
            throw new SQLException("El producto seleccionado ya no existe o esta deshabilitado.");
        }
        return producto.getStock();
    }

    private void seleccionarLinea(int idProducto) {
        for (int i = 0; i < modeloCarrito.getRowCount(); i++) {
            if (((Integer) modeloCarrito.getValueAt(i, 0)).intValue() == idProducto) {
                tablaCarrito.setRowSelectionInterval(i, i);
                return;
            }
        }
    }

    private BigDecimal totalCarrito() {
        BigDecimal total = BigDecimal.ZERO;
        for (LineaVenta linea : carrito) {
            total = total.add(linea.getSubtotal());
        }
        return total;
    }

    private LineaVenta buscarLinea(int idProducto) {
        for (LineaVenta linea : carrito) {
            if (linea.getIdProducto() == idProducto) {
                return linea;
            }
        }
        return null;
    }

    private void limpiarFormularioEntrega() {
        txtDireccionEntrega.setText("");
        txtNumeroEntrega.setText("");
        for (int i = 0; i < comboDistritoEntrega.getItemCount(); i++) {
            if ("Otro".equals(comboDistritoEntrega.getItemAt(i).getNombre())) {
                comboDistritoEntrega.setSelectedIndex(i);
                break;
            }
        }
    }

    private void actualizarBotonesVoucher() {
        boolean tieneTicket = ultimoTicket != null;
        btnImprimir.setEnabled(tieneTicket);
        btnExportar.setEnabled(tieneTicket);
    }

    private void actualizarAccionesReserva() {
        boolean tieneReserva = tablaReservas.getSelectedRow() >= 0;
        btnVenderReserva.setEnabled(tieneReserva);
        btnCancelarReserva.setEnabled(tieneReserva);
    }

    private class CantidadCellEditor extends DefaultCellEditor {
        CantidadCellEditor() {
            super(new JTextField());
            final JTextField field = (JTextField) getComponent();
            field.setHorizontalAlignment(JTextField.RIGHT);
            ((PlainDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
                @Override
                public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr)
                        throws BadLocationException {
                    if (esNumero(text)) {
                        super.insertString(fb, offset, text, attr);
                        notificarCambio();
                    }
                }

                @Override
                public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
                    super.remove(fb, offset, length);
                    notificarCambio();
                }

                @Override
                public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                        throws BadLocationException {
                    if (esNumero(text)) {
                        super.replace(fb, offset, length, text, attrs);
                        notificarCambio();
                    }
                }

                private void notificarCambio() {
                    SwingUtilities.invokeLater(new Runnable() {
                        @Override
                        public void run() {
                            actualizarCantidadMientrasEdita(field.getText());
                        }
                    });
                }

                private boolean esNumero(String text) {
                    return text == null || text.isEmpty() || text.matches("[0-9]+");
                }
            });
        }
    }

}

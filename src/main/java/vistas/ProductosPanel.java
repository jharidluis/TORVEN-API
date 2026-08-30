package vistas;

import dao.ProductoDAO;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import modelos.Producto;

public class ProductosPanel extends JPanel {
    private final ProductoDAO dao = new ProductoDAO();
    private final JTextField txtNombre = new JTextField(24);
    private final JTextField txtPrecio = new JTextField(10);
    private final JTextField txtStock = new JTextField(8);
    private final JTextField txtBuscar = new JTextField(24);
    private final JTable tabla = new JTable();
    private final DefaultTableModel modelo = Ui.modelo("ID", "Nombre", "Precio", "Stock");
    private final JButton btnLimpiar = new JButton("Limpiar");
    private final JButton btnGuardar = new JButton("Nuevo producto");
    private final JButton btnEliminar = new JButton("Deshabilitar producto");
    private int productoSeleccionadoId;
    private int versionCarga;
    private SwingWorker<List<Producto>, Void> cargaActual;

    public ProductosPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Ui.COLOR_FONDO);
        construirFormulario();
        construirTabla();
        Ui.alCambiarTexto(txtBuscar, new Runnable() {
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
    }

    public void cargar() {
        final String filtro = txtBuscar.getText();
        final int version = ++versionCarga;
        if (cargaActual != null && !cargaActual.isDone()) {
            cargaActual.cancel(true);
        }
        cargaActual = new SwingWorker<List<Producto>, Void>() {
            @Override
            protected List<Producto> doInBackground() throws Exception {
                return dao.listar(filtro);
            }

            @Override
            protected void done() {
                if (version != versionCarga) {
                    return;
                }
                try {
                    aplicarProductos(get());
                } catch (CancellationException ignored) {
                    // Una busqueda mas reciente reemplazo esta consulta.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Ui.error(ProductosPanel.this, excepcionReal(ex));
                }
            }
        };
        cargaActual.execute();
    }

    private void aplicarProductos(List<Producto> productos) {
        modelo.setRowCount(0);
        for (Producto producto : productos) {
            modelo.addRow(new Object[]{
                producto.getId(),
                producto.getNombre(),
                producto.getPrecio(),
                producto.getStock()
            });
        }
    }

    private Exception excepcionReal(ExecutionException ex) {
        Throwable causa = ex.getCause();
        return causa instanceof Exception
                ? (Exception) causa
                : new SQLException("No se pudieron actualizar los datos de productos.", causa);
    }

    private void construirFormulario() {
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createTitledBorder("Datos del producto"));
        Ui.tarjeta(formulario);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        agregar(formulario, c, 0, 0, "Nombre", txtNombre);
        agregar(formulario, c, 1, 0, "Precio", txtPrecio);
        agregar(formulario, c, 2, 0, "Stock", txtStock);

        JButton btnEliminados = new JButton("Gestionar eliminados");
        Ui.estilizarBotonSecundario(btnLimpiar);
        Ui.estilizarBotonPrimario(btnGuardar);
        Ui.estilizarBotonSecundario(btnEliminar);
        Ui.estilizarBotonSecundario(btnEliminados);
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.add(btnLimpiar);
        acciones.add(btnGuardar);
        acciones.add(btnEliminar);
        acciones.add(btnEliminados);

        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 3;
        formulario.add(acciones, c);

        btnLimpiar.addActionListener(e -> limpiar());
        btnGuardar.addActionListener(e -> guardar());
        btnEliminar.addActionListener(e -> eliminar());
        btnEliminados.addActionListener(e -> mostrarEliminados());
        actualizarModoFormulario();
        add(formulario, BorderLayout.NORTH);
    }

    private void construirTabla() {
        tabla.setModel(modelo);
        Ui.prepararTabla(tabla);
        Ui.ocultarColumna(tabla, 0);
        Ui.anchoColumna(tabla, 1, 260);
        Ui.anchoColumna(tabla, 2, 90);
        Ui.anchoColumna(tabla, 3, 70);
        Ui.columnaDinero(tabla, 2);
        Ui.columnaDerecha(tabla, 3);
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                seleccionar();
            }
        });

        JPanel panelTabla = new JPanel(new BorderLayout(8, 8));
        Ui.tarjeta(panelTabla);
        JPanel busqueda = new JPanel(new FlowLayout(FlowLayout.LEFT));
        busqueda.setOpaque(false);
        busqueda.add(new JLabel("Buscar"));
        busqueda.add(txtBuscar);
        panelTabla.add(busqueda, BorderLayout.NORTH);
        panelTabla.add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(panelTabla, BorderLayout.CENTER);
    }

    private void agregar(JPanel panel, GridBagConstraints c, int x, int y, String label, JTextField field) {
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = 1;
        panel.add(new JLabel(label), c);
        c.gridy = y + 1;
        panel.add(field, c);
    }

    private void guardar() {
        try {
            Producto producto = new Producto();
            producto.setId(productoSeleccionadoId);
            producto.setNombre(txtNombre.getText());
            producto.setPrecio(Ui.leerDinero(txtPrecio.getText()));
            producto.setStock(Ui.leerEntero(txtStock.getText(), "stock"));
            dao.guardar(producto);
            limpiar();
            DatosEventBus.publicarProductos();
            Ui.info(this, "Producto guardado correctamente.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void eliminar() {
        try {
            int id = productoSeleccionadoId;
            if (id <= 0) {
                Ui.aviso(this, "Selecciona un producto antes de deshabilitarlo.");
                return;
            }
            if (!Ui.confirmar(this, "Deshabilitar este producto? Ya no aparecera para vender.")) {
                return;
            }
            dao.eliminar(id);
            limpiar();
            DatosEventBus.publicarProductos();
            Ui.info(this, "Producto deshabilitado.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void mostrarEliminados() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = new JDialog(owner, "Productos eliminados", Dialog.ModalityType.APPLICATION_MODAL);
        final DefaultTableModel modeloEliminados = Ui.modelo("ID", "Producto", "Precio", "Stock");
        final JTable tablaEliminados = new JTable(modeloEliminados);
        Ui.prepararTabla(tablaEliminados);
        Ui.ocultarColumna(tablaEliminados, 0);
        Ui.anchoColumna(tablaEliminados, 1, 280);
        Ui.anchoColumna(tablaEliminados, 2, 90);
        Ui.anchoColumna(tablaEliminados, 3, 70);
        Ui.columnaDinero(tablaEliminados, 2);
        Ui.columnaDerecha(tablaEliminados, 3);

        JButton btnReactivar = new JButton("Reactivar");
        JButton btnCerrar = new JButton("Cerrar");
        Ui.estilizarBotonPrimario(btnReactivar);
        Ui.estilizarBotonSecundario(btnCerrar);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acciones.add(btnReactivar);
        acciones.add(btnCerrar);

        JPanel contenido = new JPanel(new BorderLayout(8, 8));
        contenido.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        contenido.setBackground(Ui.COLOR_FONDO);
        JLabel titulo = new JLabel("Productos deshabilitados");
        titulo.setFont(titulo.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        contenido.add(titulo, BorderLayout.NORTH);
        contenido.add(new JScrollPane(tablaEliminados), BorderLayout.CENTER);
        contenido.add(acciones, BorderLayout.SOUTH);

        btnReactivar.addActionListener(e -> reactivarProducto(tablaEliminados, modeloEliminados));
        btnCerrar.addActionListener(e -> dialog.dispose());

        dialog.add(contenido);
        dialog.setSize(680, 400);
        dialog.setLocationRelativeTo(this);
        cargarEliminados(modeloEliminados);
        dialog.setVisible(true);
    }

    private void cargarEliminados(DefaultTableModel modeloEliminados) {
        try {
            modeloEliminados.setRowCount(0);
            List<Producto> productos = dao.listarEliminados();
            for (Producto producto : productos) {
                modeloEliminados.addRow(new Object[]{
                    producto.getId(),
                    producto.getNombre(),
                    producto.getPrecio(),
                    producto.getStock()
                });
            }
        } catch (SQLException ex) {
            Ui.error(this, ex);
        }
    }

    private void reactivarProducto(JTable tablaEliminados, DefaultTableModel modeloEliminados) {
        int row = tablaEliminados.getSelectedRow();
        if (row < 0) {
            Ui.aviso(this, "Selecciona un producto eliminado antes de reactivarlo.");
            return;
        }
        try {
            int id = ((Number) modeloEliminados.getValueAt(row, 0)).intValue();
            dao.reactivar(id);
            cargarEliminados(modeloEliminados);
            DatosEventBus.publicarProductos();
            Ui.info(this, "Producto reactivado.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void seleccionar() {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            return;
        }
        productoSeleccionadoId = ((Integer) modelo.getValueAt(row, 0)).intValue();
        txtNombre.setText(String.valueOf(modelo.getValueAt(row, 1)));
        txtPrecio.setText(Ui.dinero((BigDecimal) modelo.getValueAt(row, 2)));
        txtStock.setText(String.valueOf(modelo.getValueAt(row, 3)));
        actualizarModoFormulario();
    }

    private void limpiar() {
        productoSeleccionadoId = 0;
        txtNombre.setText("");
        txtPrecio.setText("");
        txtStock.setText("");
        tabla.clearSelection();
        actualizarModoFormulario();
        txtNombre.requestFocusInWindow();
    }

    private void actualizarModoFormulario() {
        boolean editando = productoSeleccionadoId > 0;
        btnGuardar.setText(editando ? "Actualizar producto" : "Nuevo producto");
        btnEliminar.setEnabled(editando);
    }
}

package vistas;

import dao.ClienteDAO;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JDialog;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import modelos.Cliente;
import modelos.Distrito;

public class ClientesPanel extends JPanel {
    private final ClienteDAO dao = new ClienteDAO();
    private final JTextField txtId = new JTextField(8);
    private final JTextField txtNombre = new JTextField(24);
    private final JTextField txtTelefono = new JTextField(16);
    private final JTextField txtDireccion = new JTextField(28);
    private final JComboBox<Distrito> cboDistrito = new JComboBox<Distrito>();
    private final JTextField txtBuscar = new JTextField(24);
    private final JTable tabla = new JTable();
    private final DefaultTableModel modelo = Ui.modelo("ID", "Nombre completo", "Telefono",
            "Direccion", "Distrito", "ID Distrito");
    private final JButton btnLimpiar = new JButton("Limpiar");
    private final JButton btnGuardar = new JButton("Nuevo cliente");
    private final JButton btnEliminar = new JButton("Deshabilitar cliente");
    private int versionCarga;
    private SwingWorker<List<Cliente>, Void> cargaActual;

    public ClientesPanel() {
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        setBackground(Ui.COLOR_FONDO);
        construirFormulario();
        construirTabla();
        cargarDistritos();
        Ui.alCambiarTexto(txtBuscar, new Runnable() {
            @Override
            public void run() {
                cargar();
            }
        });
        DatosEventBus.alCambiarClientes(new Runnable() {
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
        cargaActual = new SwingWorker<List<Cliente>, Void>() {
            @Override
            protected List<Cliente> doInBackground() throws Exception {
                return dao.listar(filtro);
            }

            @Override
            protected void done() {
                if (version != versionCarga) {
                    return;
                }
                try {
                    aplicarClientes(get());
                } catch (CancellationException ignored) {
                    // Una busqueda mas reciente reemplazo esta consulta.
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException ex) {
                    Ui.error(ClientesPanel.this, excepcionReal(ex));
                }
            }
        };
        cargaActual.execute();
    }

    private void aplicarClientes(List<Cliente> clientes) {
        modelo.setRowCount(0);
        for (Cliente cliente : clientes) {
            modelo.addRow(new Object[]{
                cliente.getId(),
                cliente.getNombre(),
                texto(cliente.getNumero()),
                cliente.getDireccion(),
                texto(cliente.getDistrito()),
                cliente.getIdDistrito()
            });
        }
    }

    private Exception excepcionReal(ExecutionException ex) {
        Throwable causa = ex.getCause();
        return causa instanceof Exception
                ? (Exception) causa
                : new SQLException("No se pudieron actualizar los datos de clientes.", causa);
    }

    private void construirFormulario() {
        txtId.setEditable(false);
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBorder(BorderFactory.createTitledBorder("Datos del cliente"));
        Ui.tarjeta(formulario);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.fill = GridBagConstraints.HORIZONTAL;

        agregar(formulario, c, 0, 0, "ID", txtId);
        agregar(formulario, c, 1, 0, "Nombre completo", txtNombre);
        agregar(formulario, c, 2, 0, "Telefono (opcional)", txtTelefono);
        agregar(formulario, c, 0, 2, "Direccion", txtDireccion);
        prepararComboDistrito();
        agregar(formulario, c, 1, 2, "Distrito", cboDistrito);

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
        c.gridy = 4;
        c.gridwidth = 4;
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
        Ui.ocultarColumna(tabla, 5);
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

    private void agregar(JPanel panel, GridBagConstraints c, int x, int y, String label, JComponent field) {
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = 1;
        panel.add(new JLabel(label), c);
        c.gridy = y + 1;
        panel.add(field, c);
    }

    private void prepararComboDistrito() {
        cboDistrito.setPrototypeDisplayValue(new Distrito(0, "Villa María del Triunfo"));
        cboDistrito.setMaximumRowCount(12);
        cboDistrito.setBackground(Color.WHITE);
        cboDistrito.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                    boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Distrito) {
                    setText(((Distrito) value).getNombre());
                }
                setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
                if (!isSelected) {
                    setForeground(Ui.COLOR_TEXTO);
                    setBackground(Color.WHITE);
                }
                return this;
            }
        });
    }

    private void cargarDistritos() {
        try {
            cboDistrito.removeAllItems();
            List<Distrito> distritos = dao.listarDistritos();
            for (Distrito distrito : distritos) {
                cboDistrito.addItem(distrito);
            }
            seleccionarDistritoPorNombre("Otro");
        } catch (SQLException ex) {
            Ui.error(this, ex);
        }
    }

    private void guardar() {
        try {
            Cliente cliente = new Cliente();
            cliente.setId(txtId.getText().trim().isEmpty() ? 0 : Integer.parseInt(txtId.getText().trim()));
            cliente.setNombre(txtNombre.getText());
            cliente.setNumero(txtTelefono.getText());
            cliente.setDireccion(txtDireccion.getText());
            Distrito distrito = distritoSeleccionado();
            if (distrito != null) {
                cliente.setIdDistrito(distrito.getId());
                cliente.setDistrito(distrito.getNombre());
            }
            dao.guardar(cliente);
            limpiar();
            DatosEventBus.publicarClientes();
            Ui.info(this, "Cliente guardado correctamente.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void eliminar() {
        try {
            int id = Ui.leerEntero(txtId.getText(), "un cliente");
            if (!Ui.confirmar(this, "Deshabilitar este cliente? Ya no aparecera para nuevas ventas.")) {
                return;
            }
            dao.eliminar(id);
            limpiar();
            DatosEventBus.publicarClientes();
            Ui.info(this, "Cliente deshabilitado.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void mostrarEliminados() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        final JDialog dialog = new JDialog(owner, "Clientes eliminados", Dialog.ModalityType.APPLICATION_MODAL);
        final DefaultTableModel modeloEliminados = Ui.modelo("ID", "Nombre completo", "Telefono", "Distrito");
        final JTable tablaEliminados = new JTable(modeloEliminados);
        Ui.prepararTabla(tablaEliminados);
        Ui.ocultarColumna(tablaEliminados, 0);
        Ui.anchoColumna(tablaEliminados, 1, 260);
        Ui.anchoColumna(tablaEliminados, 2, 110);
        Ui.anchoColumna(tablaEliminados, 3, 160);

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
        JLabel titulo = new JLabel("Clientes deshabilitados");
        titulo.setFont(titulo.getFont().deriveFont(java.awt.Font.BOLD, 16f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        contenido.add(titulo, BorderLayout.NORTH);
        contenido.add(new JScrollPane(tablaEliminados), BorderLayout.CENTER);
        contenido.add(acciones, BorderLayout.SOUTH);

        btnReactivar.addActionListener(e -> reactivarCliente(tablaEliminados, modeloEliminados));
        btnCerrar.addActionListener(e -> dialog.dispose());

        dialog.add(contenido);
        dialog.setSize(760, 420);
        dialog.setLocationRelativeTo(this);
        cargarEliminados(modeloEliminados);
        dialog.setVisible(true);
    }

    private void cargarEliminados(DefaultTableModel modeloEliminados) {
        try {
            modeloEliminados.setRowCount(0);
            List<Cliente> clientes = dao.listarEliminados();
            for (Cliente cliente : clientes) {
                modeloEliminados.addRow(new Object[]{
                    cliente.getId(),
                    cliente.getNombre(),
                    texto(cliente.getNumero()),
                    texto(cliente.getDistrito())
                });
            }
        } catch (SQLException ex) {
            Ui.error(this, ex);
        }
    }

    private void reactivarCliente(JTable tablaEliminados, DefaultTableModel modeloEliminados) {
        int row = tablaEliminados.getSelectedRow();
        if (row < 0) {
            Ui.aviso(this, "Selecciona un cliente eliminado antes de reactivarlo.");
            return;
        }
        try {
            int id = ((Number) modeloEliminados.getValueAt(row, 0)).intValue();
            dao.reactivar(id);
            cargarEliminados(modeloEliminados);
            DatosEventBus.publicarClientes();
            Ui.info(this, "Cliente reactivado.");
        } catch (Exception ex) {
            Ui.error(this, ex);
        }
    }

    private void seleccionar() {
        int row = tabla.getSelectedRow();
        if (row < 0) {
            return;
        }
        txtId.setText(String.valueOf(modelo.getValueAt(row, 0)));
        txtNombre.setText(String.valueOf(modelo.getValueAt(row, 1)));
        txtTelefono.setText(String.valueOf(modelo.getValueAt(row, 2)));
        txtDireccion.setText(String.valueOf(modelo.getValueAt(row, 3)));
        Object idDistrito = modelo.getValueAt(row, 5);
        if (idDistrito instanceof Number) {
            seleccionarDistritoPorId(((Number) idDistrito).intValue());
        } else {
            seleccionarDistritoPorNombre(String.valueOf(modelo.getValueAt(row, 4)));
        }
        actualizarModoFormulario();
    }

    private void limpiar() {
        txtId.setText("");
        txtNombre.setText("");
        txtTelefono.setText("");
        txtDireccion.setText("");
        seleccionarDistritoPorNombre("Otro");
        tabla.clearSelection();
        actualizarModoFormulario();
        txtNombre.requestFocusInWindow();
    }

    private void actualizarModoFormulario() {
        boolean editando = !txtId.getText().trim().isEmpty();
        btnGuardar.setText(editando ? "Actualizar cliente" : "Nuevo cliente");
        btnEliminar.setEnabled(editando);
    }

    private Distrito distritoSeleccionado() {
        Object item = cboDistrito.getSelectedItem();
        return item instanceof Distrito ? (Distrito) item : null;
    }

    private void seleccionarDistritoPorId(int idDistrito) {
        for (int i = 0; i < cboDistrito.getItemCount(); i++) {
            Distrito distrito = cboDistrito.getItemAt(i);
            if (distrito.getId() == idDistrito) {
                cboDistrito.setSelectedIndex(i);
                return;
            }
        }
        seleccionarDistritoPorNombre("Otro");
    }

    private void seleccionarDistritoPorNombre(String nombre) {
        String buscado = nombre == null ? "" : nombre.trim();
        for (int i = 0; i < cboDistrito.getItemCount(); i++) {
            Distrito distrito = cboDistrito.getItemAt(i);
            if (distrito.getNombre().equalsIgnoreCase(buscado)) {
                cboDistrito.setSelectedIndex(i);
                return;
            }
        }
        if (cboDistrito.getItemCount() > 0) {
            cboDistrito.setSelectedIndex(cboDistrito.getItemCount() - 1);
        }
    }

    private String texto(String value) {
        return value == null ? "" : value;
    }
}

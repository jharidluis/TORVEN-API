package vistas;

import dao.UsuarioDAO;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.sql.SQLException;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import modelos.Usuario;

public class CambioClaveDialog extends JDialog {
    private final UsuarioDAO dao = new UsuarioDAO();
    private final Usuario usuario;
    private final JTextField txtUsuario = new JTextField(18);
    private final JPasswordField txtNueva = new JPasswordField(18);
    private final JPasswordField txtConfirmar = new JPasswordField(18);
    private boolean claveCambiada;
    private String nuevoUsuario;

    public CambioClaveDialog(Window owner, Usuario usuario) {
        super(owner, "Torven | Configurar acceso", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        setIconImage(AppIcon.crear(128));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        construir();
        pack();
        setLocationRelativeTo(owner);
    }

    public boolean isClaveCambiada() {
        return claveCambiada;
    }

    public String getNuevoUsuario() {
        return nuevoUsuario;
    }

    private void construir() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setBackground(Ui.COLOR_FONDO);

        JPanel encabezado = new JPanel(new GridBagLayout());
        encabezado.setOpaque(false);
        GridBagConstraints h = new GridBagConstraints();
        h.gridx = 0;
        h.anchor = GridBagConstraints.CENTER;
        h.insets = new Insets(0, 0, 6, 0);
        encabezado.add(new JLabel(new ImageIcon(AppIcon.crearLogo(220, 70))), h);

        JLabel titulo = new JLabel("Configura tu acceso");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 19f));
        h.gridy = 1;
        h.insets = new Insets(0, 0, 2, 0);
        encabezado.add(titulo, h);

        JLabel detalle = new JLabel("Primer ingreso: cambia usuario y clave");
        detalle.setForeground(Ui.COLOR_MUTED);
        h.gridy = 2;
        h.insets = new Insets(0, 0, 0, 0);
        encabezado.add(detalle, h);
        root.add(encabezado, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        Ui.tarjeta(form);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        txtUsuario.setText(usuario.getUsuario());
        agregarCampo(form, c, 0, "Nuevo usuario", txtUsuario);
        agregarCampo(form, c, 1, "Nueva clave", txtNueva);
        agregarCampo(form, c, 2, "Confirmar clave", txtConfirmar);
        root.add(form, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acciones.setOpaque(false);
        JButton cancelar = new JButton("Cancelar");
        JButton guardar = new JButton("Guardar acceso");
        Ui.estilizarBotonSecundario(cancelar);
        Ui.estilizarBotonPrimario(guardar);
        acciones.add(cancelar);
        acciones.add(guardar);
        root.add(acciones, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(guardar);
        cancelar.addActionListener(e -> dispose());
        guardar.addActionListener(e -> guardar());
        add(root);
    }

    private void agregarCampo(JPanel panel, GridBagConstraints c, int fila, String label, JTextField field) {
        c.gridx = 0;
        c.gridy = fila;
        c.weightx = 0;
        panel.add(new JLabel(label), c);

        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private void guardar() {
        String usuarioNuevo = txtUsuario.getText();
        char[] nueva = txtNueva.getPassword();
        char[] confirmar = txtConfirmar.getPassword();
        try {
            if (!Arrays.equals(nueva, confirmar)) {
                throw new SQLException("Las claves no coinciden.");
            }
            dao.cambiarUsuarioYClave(usuario.getId(), usuarioNuevo, nueva);
            nuevoUsuario = usuarioNuevo == null ? "" : usuarioNuevo.trim();
            claveCambiada = true;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
            txtNueva.setText("");
            txtConfirmar.setText("");
            if (txtUsuario.getText().trim().isEmpty()) {
                txtUsuario.requestFocusInWindow();
            } else {
                txtNueva.requestFocusInWindow();
            }
        } finally {
            Arrays.fill(nueva, '\0');
            Arrays.fill(confirmar, '\0');
        }
    }
}

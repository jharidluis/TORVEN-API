package vistas;

import configuracion.AuditoriaContext;
import dao.UsuarioDAO;
import java.awt.BorderLayout;
import java.awt.Color;
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

public class LoginDialog extends JDialog {
    private final UsuarioDAO dao = new UsuarioDAO();
    private final JTextField txtUsuario = new JTextField(18);
    private final JPasswordField txtClave = new JPasswordField(18);
    private Usuario usuarioAutenticado;

    public LoginDialog(Window owner) {
        super(owner, "Torven | Ingreso al sistema", Dialog.ModalityType.APPLICATION_MODAL);
        setIconImage(AppIcon.crear(128));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        construir();
        pack();
        setLocationRelativeTo(owner);
    }

    public Usuario getUsuarioAutenticado() {
        return usuarioAutenticado;
    }

    private void construir() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        root.setBackground(Ui.COLOR_FONDO);

        JPanel marca = new JPanel(new GridBagLayout());
        marca.setOpaque(false);
        GridBagConstraints t = new GridBagConstraints();
        t.gridx = 0;
        t.anchor = GridBagConstraints.CENTER;
        t.insets = new Insets(0, 0, 6, 0);
        JLabel logo = new JLabel(new ImageIcon(AppIcon.crearLogo(260, 84)));
        marca.add(logo, t);

        JLabel titulo = new JLabel("Sistema de ventas");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        titulo.setForeground(Ui.COLOR_TEXTO);
        t.gridy = 1;
        t.insets = new Insets(0, 0, 2, 0);
        marca.add(titulo, t);

        JLabel subtitulo = new JLabel("Ingresa con tu usuario");
        subtitulo.setForeground(Ui.COLOR_MUTED);
        t.gridy = 2;
        t.insets = new Insets(0, 0, 0, 0);
        marca.add(subtitulo, t);
        root.add(marca, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        Ui.tarjeta(form);
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        agregarCampo(form, c, 0, "Usuario", txtUsuario);
        agregarCampo(form, c, 1, "Clave", txtClave);
        root.add(form, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        acciones.setOpaque(false);
        JButton cancelar = new JButton("Cancelar");
        JButton ingresar = new JButton("Ingresar");
        Ui.estilizarBotonSecundario(cancelar);
        Ui.estilizarBotonPrimario(ingresar);
        acciones.add(cancelar);
        acciones.add(ingresar);
        root.add(acciones, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(ingresar);
        cancelar.addActionListener(e -> dispose());
        ingresar.addActionListener(e -> autenticar());
        add(root);
    }

    private void agregarCampo(JPanel panel, GridBagConstraints c, int fila, String label, JTextField field) {
        c.gridx = 0;
        c.gridy = fila;
        c.weightx = 0;
        JLabel etiqueta = new JLabel(label);
        etiqueta.setForeground(new Color(55, 65, 81));
        panel.add(etiqueta, c);

        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
    }

    private void autenticar() {
        char[] clave = txtClave.getPassword();
        try {
            Usuario usuario = dao.autenticar(txtUsuario.getText(), clave);
            if (usuario == null) {
                Ui.aviso(this, "El usuario o la contraseña no son correctos. Revisa los datos e inténtalo nuevamente.");
                txtClave.setText("");
                txtClave.requestFocusInWindow();
                return;
            }
            if (usuario.debeCambiarClave()) {
                AuditoriaContext.establecer(usuario);
                CambioClaveDialog cambio = new CambioClaveDialog(this, usuario);
                cambio.setVisible(true);
                if (!cambio.isClaveCambiada()) {
                    AuditoriaContext.limpiar();
                    txtClave.setText("");
                    txtClave.requestFocusInWindow();
                    return;
                }
                usuarioAutenticado = null;
                AuditoriaContext.limpiar();
                txtClave.setText("");
                if (cambio.getNuevoUsuario() != null && !cambio.getNuevoUsuario().trim().isEmpty()) {
                    txtUsuario.setText(cambio.getNuevoUsuario());
                }
                Ui.info(this, "Ingresa nuevamente con tu nuevo usuario y clave.");
                txtUsuario.requestFocusInWindow();
                return;
            }
            usuarioAutenticado = usuario;
            dispose();
        } catch (Exception ex) {
            Ui.error(this, ex);
        } finally {
            Arrays.fill(clave, '\0');
        }
    }
}

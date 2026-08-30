package vistas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.print.PrinterException;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.SQLException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;

/** Cuadros de mensaje con la identidad visual de Torven. */
public final class Mensajes {
    private static final Color FONDO = new Color(248, 250, 252);
    private static final Color TEXTO = new Color(17, 24, 39);
    private static final Color MUTED = new Color(91, 101, 117);
    private static final Color ERROR = new Color(220, 38, 38);
    private static final Color EXITO = new Color(22, 163, 74);
    private static final Color AVISO = new Color(217, 119, 6);
    private static final Color PREGUNTA = new Color(20, 184, 166);

    private Mensajes() {
    }

    public static void error(Component parent, Throwable error) {
        error(parent, "No pudimos completar la acción", mensajeAmigable(error), error, null);
    }

    public static void error(Component parent, String titulo, String mensaje, Throwable error) {
        error(parent, titulo, mensaje, error, null);
    }

    public static void error(Component parent, String titulo, String mensaje, Throwable error, String contexto) {
        String detalles = detallesTecnicos(error, contexto);
        mostrar(parent, Tipo.ERROR, titulo, mensaje, detalles, new String[]{"Entendido"}, 0);
    }

    public static void exito(Component parent, String mensaje) {
        mostrar(parent, Tipo.EXITO, "Operación completada", mensaje, null,
                new String[]{"Aceptar"}, 0);
    }

    public static void aviso(Component parent, String mensaje) {
        mostrar(parent, Tipo.AVISO, "Revisa esta información", mensaje, null,
                new String[]{"Entendido"}, 0);
    }

    public static void licenciaSuspendida(Component parent, String mensaje) {
        mostrar(parent, Tipo.AVISO, "Acceso suspendido", mensaje, null,
                new String[]{"Cerrar"}, 0);
    }

    public static boolean confirmar(Component parent, String mensaje) {
        int resultado = mostrar(parent, Tipo.PREGUNTA, "Confirma la acción", mensaje, null,
                new String[]{"Cancelar", "Confirmar"}, 1);
        return resultado == 1;
    }

    public static int elegir(Component parent, String titulo, String mensaje,
            String[] opciones, int opcionPrincipal) {
        return mostrar(parent, Tipo.PREGUNTA, titulo, mensaje, null, opciones, opcionPrincipal);
    }

    private static int mostrar(Component parent, Tipo tipo, String titulo, String mensaje,
            String detalles, String[] opciones, int opcionPrincipal) {
        final int[] resultado = {-1};
        final Window owner = parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent);
        final JDialog dialog = new JDialog(owner, "Torven", JDialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.setIconImage(AppIcon.crear(32));

        final JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(FONDO);
        root.setBorder(new EmptyBorder(20, 22, 18, 22));

        JPanel contenido = new JPanel(new BorderLayout(16, 0));
        contenido.setOpaque(false);
        contenido.add(new IconoEstado(tipo), BorderLayout.WEST);

        JPanel textos = new JPanel(new BorderLayout(0, 7));
        textos.setOpaque(false);

        JLabel marca = new JLabel("TORVEN");
        marca.setForeground(color(tipo));
        marca.setFont(new Font("Segoe UI", Font.BOLD, 11));

        JLabel lblTitulo = new JLabel(textoHtml(titulo, 370));
        lblTitulo.setForeground(TEXTO);
        lblTitulo.setFont(new Font("Segoe UI", Font.BOLD, 18));

        JLabel lblMensaje = new JLabel(textoHtml(mensaje, 370));
        lblMensaje.setForeground(MUTED);
        lblMensaje.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel encabezado = new JPanel(new BorderLayout(0, 3));
        encabezado.setOpaque(false);
        encabezado.add(marca, BorderLayout.NORTH);
        encabezado.add(lblTitulo, BorderLayout.CENTER);
        textos.add(encabezado, BorderLayout.NORTH);
        textos.add(lblMensaje, BorderLayout.CENTER);
        contenido.add(textos, BorderLayout.CENTER);
        root.add(contenido, BorderLayout.NORTH);

        final JScrollPane panelDetalles;
        if (detalles != null && !detalles.trim().isEmpty()) {
            JTextArea area = new JTextArea(detalles, 7, 48);
            area.setEditable(false);
            area.setLineWrap(false);
            area.setCaretPosition(0);
            area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            area.setForeground(new Color(55, 65, 81));
            area.setBackground(Color.WHITE);
            area.setBorder(new EmptyBorder(8, 8, 8, 8));
            panelDetalles = new JScrollPane(area);
            panelDetalles.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(new Color(218, 224, 232)), "Detalles técnicos"));
            panelDetalles.setPreferredSize(new Dimension(480, 150));
            panelDetalles.setVisible(false);
            root.add(panelDetalles, BorderLayout.CENTER);
        } else {
            panelDetalles = null;
        }

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        acciones.setOpaque(false);

        if (panelDetalles != null) {
            final JButton verDetalles = new JButton("Ver detalles");
            Ui.estilizarBotonSecundario(verDetalles);
            verDetalles.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean mostrar = !panelDetalles.isVisible();
                    panelDetalles.setVisible(mostrar);
                    verDetalles.setText(mostrar ? "Ocultar detalles" : "Ver detalles");
                    dialog.pack();
                    dialog.setLocationRelativeTo(owner);
                }
            });
            acciones.add(verDetalles);
        }

        JButton botonPrincipal = null;
        for (int i = 0; i < opciones.length; i++) {
            final int indice = i;
            JButton boton = new JButton(opciones[i]);
            if (i == opcionPrincipal) {
                Ui.estilizarBotonPrimario(boton);
                botonPrincipal = boton;
            } else {
                Ui.estilizarBotonSecundario(boton);
            }
            boton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    resultado[0] = indice;
                    dialog.dispose();
                }
            });
            acciones.add(boton);
        }
        root.add(acciones, BorderLayout.SOUTH);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                resultado[0] = -1;
            }
        });
        dialog.setContentPane(root);
        if (botonPrincipal != null) {
            dialog.getRootPane().setDefaultButton(botonPrincipal);
        }
        dialog.pack();
        dialog.setMinimumSize(new Dimension(520, dialog.getHeight()));
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return resultado[0];
    }

    private static String mensajeAmigable(Throwable error) {
        if (error == null) {
            return "Ocurrió un inconveniente inesperado. Inténtalo nuevamente.";
        }
        Throwable actual = error;
        while (actual.getCause() != null && actual.getCause() != actual) {
            actual = actual.getCause();
        }

        String mensaje = primerMensaje(error, actual);
        String minusculas = mensaje.toLowerCase(java.util.Locale.ROOT);

        if (esSql(error, actual)) {
            if (minusculas.contains("communications link failure")
                    || minusculas.contains("connection refused")
                    || minusculas.contains("connect timed out")
                    || minusculas.contains("no operations allowed after connection closed")) {
                return "No pudimos conectarnos con la base de datos. Verifica que MySQL esté encendido y vuelve a intentarlo.";
            }
            if (minusculas.contains("access denied for user")) {
                return "No pudimos acceder a la base de datos. Revisa el usuario y la contraseña configurados.";
            }
            if (minusculas.contains("unknown database")) {
                return "La base de datos configurada no existe. Revisa la configuración de Torven.";
            }
            if (minusculas.contains("duplicate entry")) {
                return "Ya existe un registro con esos datos. Revísalos antes de guardar.";
            }
            if (minusculas.contains("foreign key constraint fails")) {
                return "No se puede completar la acción porque este registro está siendo utilizado.";
            }
        }
        if (actual instanceof PrinterException || minusculas.contains("printer")) {
            return "No pudimos enviar el voucher a la impresora. Revisa que esté conectada y disponible.";
        }
        if (actual instanceof IOException) {
            return "No pudimos leer o guardar el archivo. Revisa la ubicación elegida y vuelve a intentarlo.";
        }
        if (mensaje.length() > 280 || minusculas.startsWith("com.mysql.") || minusculas.contains("jdbc:mysql:")) {
            return "Ocurrió un inconveniente inesperado. Inténtalo nuevamente o revisa los detalles técnicos.";
        }
        return mensaje;
    }

    private static boolean esSql(Throwable primero, Throwable ultimo) {
        return primero instanceof SQLException || ultimo instanceof SQLException;
    }

    private static String primerMensaje(Throwable primero, Throwable ultimo) {
        String mensaje = primero.getMessage();
        if (mensaje == null || mensaje.trim().isEmpty()) {
            mensaje = ultimo.getMessage();
        }
        if (mensaje == null || mensaje.trim().isEmpty()) {
            return "Ocurrió un inconveniente inesperado. Inténtalo nuevamente.";
        }
        return mensaje.trim();
    }

    private static String detallesTecnicos(Throwable error, String contexto) {
        if (error == null && (contexto == null || contexto.trim().isEmpty())) {
            return null;
        }
        StringBuilder detalles = new StringBuilder();
        if (contexto != null && !contexto.trim().isEmpty()) {
            detalles.append(contexto.trim()).append("\n\n");
        }
        if (error != null) {
            StringWriter writer = new StringWriter();
            error.printStackTrace(new PrintWriter(writer));
            detalles.append(writer.toString());
        }
        return detalles.toString();
    }

    private static String textoHtml(String texto, int ancho) {
        String seguro = texto == null ? "" : texto;
        seguro = seguro.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("\n", "<br>");
        return "<html><div style='width:" + ancho + "px'>" + seguro + "</div></html>";
    }

    private static Color color(Tipo tipo) {
        switch (tipo) {
            case ERROR:
                return ERROR;
            case EXITO:
                return EXITO;
            case AVISO:
                return AVISO;
            default:
                return PREGUNTA;
        }
    }

    private enum Tipo {
        ERROR("×"),
        EXITO("✓"),
        AVISO("!"),
        PREGUNTA("?");

        private final String simbolo;

        Tipo(String simbolo) {
            this.simbolo = simbolo;
        }
    }

    private static final class IconoEstado extends JComponent {
        private final Tipo tipo;

        IconoEstado(Tipo tipo) {
            this.tipo = tipo;
            setPreferredSize(new Dimension(52, 52));
            setMinimumSize(new Dimension(52, 52));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(color(tipo));
                g.fillOval(2, 2, 48, 48);
                g.setColor(Color.WHITE);
                g.setFont(new Font("Segoe UI Symbol", Font.BOLD, 25));
                FontMetrics metrics = g.getFontMetrics();
                int x = (getWidth() - metrics.stringWidth(tipo.simbolo)) / 2;
                int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
                g.drawString(tipo.simbolo, x, y);
            } finally {
                g.dispose();
            }
        }
    }
}

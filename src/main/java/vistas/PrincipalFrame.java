package vistas;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import modelos.Usuario;

public class PrincipalFrame extends JFrame {
    private final Usuario usuario;
    private final Runnable alCerrarSesion;
    private final VentasPanel ventasPanel;
    private DashboardPanel dashboardPanel;
    private ProductosPanel productosPanel;
    private ClientesPanel clientesPanel;
    private JTabbedPane tabs;

    public PrincipalFrame(Usuario usuario) {
        this(usuario, null);
    }

    public PrincipalFrame(Usuario usuario, Runnable alCerrarSesion) {
        this.usuario = usuario;
        this.alCerrarSesion = alCerrarSesion;
        this.ventasPanel = new VentasPanel(usuario);
        setTitle("Torven | Sistema de ventas");
        setIconImage(AppIcon.crear(128));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 700));
        construirMenu();
        construirContenido();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                preguntarSalirOCerrarSesion();
            }
        });
        pack();
    }

    private void construirMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu archivo = new JMenu("Archivo");
        JMenu navegar = new JMenu("Ir a");
        JMenuItem dashboard = new JMenuItem("Dashboard");
        JMenuItem ventas = new JMenuItem("Ventas");
        JMenuItem productos = new JMenuItem("Productos");
        JMenuItem clientes = new JMenuItem("Clientes");
        JMenuItem actualizar = new JMenuItem("Actualizar datos");
        JMenuItem cerrarSesion = new JMenuItem("Cerrar sesion");
        JMenuItem salir = new JMenuItem("Salir");

        dashboard.addActionListener(e -> seleccionar(0));
        ventas.addActionListener(e -> seleccionar(usuario.esAdministrador() ? 1 : 0));
        productos.addActionListener(e -> seleccionar(2));
        clientes.addActionListener(e -> seleccionar(3));
        actualizar.addActionListener(e -> cargarSegunPerfil());
        cerrarSesion.addActionListener(e -> cerrarSesion());
        salir.addActionListener(e -> salirDelSistema());

        archivo.add(actualizar);
        archivo.addSeparator();
        archivo.add(cerrarSesion);
        archivo.add(salir);
        if (usuario.esAdministrador()) {
            navegar.add(dashboard);
        }
        navegar.add(ventas);
        if (usuario.esAdministrador()) {
            navegar.add(productos);
            navegar.add(clientes);
        }
        menuBar.add(navegar);
        menuBar.add(archivo);
        setJMenuBar(menuBar);
    }

    private void construirContenido() {
        add(encabezado(), BorderLayout.NORTH);

        tabs = new JTabbedPane();
        if (usuario.esAdministrador()) {
            dashboardPanel = new DashboardPanel();
            productosPanel = new ProductosPanel();
            clientesPanel = new ClientesPanel();
            tabs.addTab("Dashboard", new ImageIcon(AppIcon.crear(18)), dashboardPanel);
        }
        tabs.addTab("Ventas", ventasPanel);
        if (usuario.esAdministrador()) {
            tabs.addTab("Productos", productosPanel);
            tabs.addTab("Clientes", clientesPanel);
        }
        tabs.addChangeListener(e -> cargarSegunPerfil());
        add(tabs, BorderLayout.CENTER);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                cargarSegunPerfil();
            }
        });
    }

    private JPanel encabezado() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(12, 14, 18));
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 18, 12, 18));

        JPanel marca = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        marca.setOpaque(false);
        JPanel placaLogo = new JPanel(new BorderLayout());
        Ui.placaBlanca(placaLogo, 7, 16);
        JLabel icono = new JLabel(new ImageIcon(AppIcon.crearLogo(148, 42)));
        placaLogo.add(icono, BorderLayout.CENTER);
        JLabel titulo = new JLabel("Sistema de ventas");
        titulo.setForeground(Color.WHITE);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 18f));
        marca.add(placaLogo);
        marca.add(titulo);

        JLabel estado = new JLabel(usuario.getNombre() + " | " + perfilVisible());
        estado.setForeground(new Color(229, 231, 235));

        panel.add(marca, BorderLayout.WEST);
        panel.add(estado, BorderLayout.EAST);
        return panel;
    }

    private void cargarSegunPerfil() {
        if (!usuario.esAdministrador()) {
            ventasPanel.cargarDatos();
            return;
        }

        int seleccion = tabs == null ? 0 : tabs.getSelectedIndex();
        if (seleccion == 0) {
            dashboardPanel.cargar();
        } else if (seleccion == 1) {
            ventasPanel.cargarDatos();
        } else if (seleccion == 2) {
            productosPanel.cargar();
        } else if (seleccion == 3) {
            clientesPanel.cargar();
        }
    }

    private String perfilVisible() {
        return usuario.esAdministrador() ? "Administrador" : "Vendedor";
    }

    private void seleccionar(int index) {
        if (tabs != null) {
            tabs.setSelectedIndex(index);
        }
    }

    private void cerrarSesion() {
        if (!Ui.confirmar(this, "Cerrar sesion y volver al login?")) {
            return;
        }
        cerrarSesionSinPreguntar();
    }

    private void cerrarSesionSinPreguntar() {
        DatosEventBus.limpiar();
        dispose();
        if (alCerrarSesion != null) {
            alCerrarSesion.run();
        }
    }

    private void preguntarSalirOCerrarSesion() {
        String[] opciones = {"Cancelar", "Salir", "Cerrar sesión"};
        int respuesta = Mensajes.elegir(
                this,
                "¿Qué deseas hacer?",
                "Puedes cerrar tu sesión para volver al acceso o salir completamente de Torven.",
                opciones,
                2);
        if (respuesta == 2) {
            cerrarSesionSinPreguntar();
        } else if (respuesta == 1) {
            salirDelSistema();
        }
    }

    private void salirDelSistema() {
        if (Ui.confirmar(this, "Salir del sistema?")) {
            dispose();
            System.exit(0);
        }
    }
}

package app;

import configuracion.AppConfig;
import configuracion.AuditoriaContext;
import configuracion.Conexion;
import licencias.LicenciaService;
import java.sql.Connection;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import modelos.Usuario;
import vistas.LoginDialog;
import vistas.Mensajes;
import vistas.PrincipalFrame;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                iniciar();
            }
        });
    }

    private static void iniciar() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {
            // Si falla el estilo del sistema, Swing usa el estilo por defecto.
        }

        try (Connection conn = Conexion.abrir()) {
            // Solo verifica que la conexion a la base de datos funcione; no migra ni crea tablas.
        } catch (Exception ex) {
            Mensajes.error(
                    null,
                    "No pudimos iniciar Torven",
                    "No fue posible preparar la base de datos. Verifica que MySQL esté encendido y que los datos de acceso sean correctos.",
                    ex,
                    "Configuración: " + rutaConfiguracion() + "\nBase de datos: " + nombreBase());
            return;
        }

        try {
            LicenciaService.Resultado licencia = LicenciaService.verificar();
            if (!licencia.isAccesoPermitido()) {
                Mensajes.licenciaSuspendida(null, licencia.getMensaje());
                return;
            }
        } catch (Exception ex) {
            Mensajes.error(
                    null,
                    "No pudimos verificar la licencia",
                    "Torven no pudo comprobar la autorización de esta instalación. Verifica tu conexión a Internet o comunícate con el proveedor.",
                    ex,
                    "Base de datos: " + nombreBase());
            return;
        }

        mostrarLogin();
    }

    private static void mostrarLogin() {
        LoginDialog login = new LoginDialog(null);
        login.setVisible(true);
        Usuario usuario = login.getUsuarioAutenticado();
        if (usuario == null) {
            AuditoriaContext.limpiar();
            System.exit(0);
            return;
        }
        AuditoriaContext.establecer(usuario);

        PrincipalFrame frame = new PrincipalFrame(usuario, new Runnable() {
            @Override
            public void run() {
                cerrarSesion();
            }
        });
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void cerrarSesion() {
        AuditoriaContext.limpiar();
        mostrarLogin();
    }

    private static String rutaConfiguracion() {
        try {
            return AppConfig.get().configPathDescription();
        } catch (Exception ex) {
            return "config/database.properties";
        }
    }

    private static String nombreBase() { 
        try {
            return AppConfig.get().databaseName();
        } catch (Exception ex) {
            return "tienda";
        }
    }
}

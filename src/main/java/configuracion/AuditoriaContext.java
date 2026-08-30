package configuracion;

import modelos.Usuario;

public final class AuditoriaContext {
    // Por-hilo en vez de estatico compartido: con la API movil, cada peticion de
    // un usuario distinto puede atenderse en un hilo distinto al mismo tiempo:
    // con un solo valor compartido, una venta podia quedar auditada con el
    // nombre de otro usuario que inicio sesion casi al mismo tiempo.
    private static final ThreadLocal<Integer> usuarioId = new ThreadLocal<Integer>();
    private static final ThreadLocal<String> usuario = new ThreadLocal<String>();

    private AuditoriaContext() {
    }

    public static void establecer(Usuario usuarioActual) {
        if (usuarioActual == null) {
            limpiar();
            return;
        }
        usuarioId.set(Integer.valueOf(usuarioActual.getId()));
        usuario.set(usuarioActual.getUsuario());
    }

    public static void limpiar() {
        usuarioId.remove();
        usuario.remove();
    }

    public static Integer usuarioId() {
        return usuarioId.get();
    }

    public static String usuario() {
        return usuario.get();
    }
}

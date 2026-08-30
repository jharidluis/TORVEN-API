package vistas;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

final class DatosEventBus {
    private static final List<Runnable> clientes = new CopyOnWriteArrayList<Runnable>();
    private static final List<Runnable> productos = new CopyOnWriteArrayList<Runnable>();
    private static final List<Runnable> ventas = new CopyOnWriteArrayList<Runnable>();

    private DatosEventBus() {
    }

    static void alCambiarClientes(Runnable listener) {
        clientes.add(listener);
    }

    static void alCambiarProductos(Runnable listener) {
        productos.add(listener);
    }

    static void alCambiarVentas(Runnable listener) {
        ventas.add(listener);
    }

    static void publicarClientes() {
        publicar(clientes);
    }

    static void publicarProductos() {
        publicar(productos);
    }

    static void publicarVentas() {
        publicar(ventas);
    }

    static void limpiar() {
        clientes.clear();
        productos.clear();
        ventas.clear();
    }

    private static void publicar(List<Runnable> listeners) {
        for (Runnable listener : listeners) {
            listener.run();
        }
    }
}

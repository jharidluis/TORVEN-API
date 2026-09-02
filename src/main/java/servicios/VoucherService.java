package servicios;

import configuracion.AppConfig;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import modelos.LineaVenta;
import modelos.VentaEstado;
import modelos.VentaTicket;

public class VoucherService {
    private static final int TICKET_WIDTH_CHARS = 48;
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    public String crearTexto(VentaTicket ticket) {
        StringBuilder texto = new StringBuilder();
        for (String linea : crearLineas(ticket)) {
            texto.append(linea).append(System.lineSeparator());
        }
        return texto.toString();
    }

    public List<String> crearLineas(VentaTicket ticket) {
        AppConfig config = AppConfig.get();
        List<String> lineas = new ArrayList<String>();

        lineas.add(repetir("=", TICKET_WIDTH_CHARS));
        lineas.add(centrar(valor(config.get("store.name"), "Torven")));
        agregarSiTiene(lineas, "RUC: " + config.get("store.ruc"));
        agregarSiTiene(lineas, config.get("store.address"));
        agregarSiTiene(lineas, "Tel: " + config.get("store.phone"));
        lineas.add(centrar(tituloComprobante(ticket)));
        lineas.add(repetir("-", TICKET_WIDTH_CHARS));
        lineas.add(campo("Ticket", String.format("%08d", ticket.getIdVenta())));
        lineas.add(campo("Fecha", FECHA.format(ticket.getFecha())));
        lineas.add(campo("Estado", VentaEstado.etiqueta(ticket.getEstado())));
        lineas.add(campo("Direccion", recortar(ticket.getLugarEntrega().getDireccion(), 35)));
        lineas.add(campo("DNI/RUC", valor(ticket.getDocumentoComprobante(), "Sin documento")));
        String distrito = valor(ticket.getLugarEntrega().getDistrito(), "");
        if (!distrito.isEmpty() && !"Sin distrito".equalsIgnoreCase(distrito)) {
            lineas.add(campo("Distrito", distrito));
        }
        lineas.add(campo("Pago", pago(ticket.getEstado())));
        lineas.add(repetir("-", TICKET_WIDTH_CHARS));
        lineas.add(String.format("%-3s %-20s %5s %7s %9s", "#", "Producto", "Cant", "P.Unit", "Importe"));
        lineas.add(repetir("-", TICKET_WIDTH_CHARS));

        int item = 1;
        for (LineaVenta linea : ticket.getLineas()) {
            String nombre = recortar(linea.getNombreProducto(), 20);
            lineas.add(String.format(Locale.US, "%-3d %-20s %5d %7s %9s",
                    item,
                    nombre,
                    linea.getCantidad(),
                    importeCorto(linea.getPrecio()),
                    importeCorto(linea.getSubtotal())));
            item++;
        }

        lineas.add(repetir("-", TICKET_WIDTH_CHARS));
        lineas.add(alinearDerecha("Subtotal: " + moneda(ticket.getTotal())));
        lineas.add(alinearDerecha("IGV:      S/. 0.00"));
        lineas.add(alinearDerecha("TOTAL:    " + moneda(ticket.getTotal())));
        lineas.add(repetir("-", TICKET_WIDTH_CHARS));
        lineas.add(centrar("Documento no valido como comprobante SUNAT"));
        lineas.add(centrar(mensajeFinal(ticket.getEstado())));
        lineas.add(repetir("=", TICKET_WIDTH_CHARS));
        return lineas;
    }

    public void exportarPdf(Path path, VentaTicket ticket) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        escribirPdf(path, crearLineas(ticket));
    }

    public void abrirArchivo(Path path) {
        if (!Desktop.isDesktopSupported()) {
            return;
        }
        try {
            Desktop.getDesktop().open(path.toFile());
        } catch (IOException ignored) {
            // El archivo ya fue generado; abrirlo es solo una comodidad.
        }
    }

    public void imprimir(final VentaTicket ticket) throws PrinterException {
        final List<String> lineas = crearLineas(ticket);
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setJobName("Ticket " + ticket.getIdVenta());
        job.setPrintable(new Printable() {
            @Override
            public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) {
                if (pageIndex > 0) {
                    return NO_SUCH_PAGE;
                }
                Graphics2D g = (Graphics2D) graphics;
                g.translate(pageFormat.getImageableX(), pageFormat.getImageableY());
                g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
                int y = 12;
                for (String linea : lineas) {
                    g.drawString(linea, 0, y);
                    y += 10;
                }
                return PAGE_EXISTS;
            }
        }, crearFormatoTicket(lineas.size()));

        if (job.printDialog()) {
            job.print();
        }
    }

    public Path rutaSugerida(VentaTicket ticket) {
        String carpeta = valor(AppConfig.get().get("voucher.folder"), "vouchers");
        return java.nio.file.Paths.get(carpeta, "ticket_" + String.format("%08d", ticket.getIdVenta()) + ".pdf");
    }

    private PageFormat crearFormatoTicket(int cantidadLineas) {
        double width = 226.77;
        double height = Math.max(300.0, cantidadLineas * 11.0 + 30.0);
        Paper paper = new Paper();
        paper.setSize(width, height);
        paper.setImageableArea(8, 8, width - 16, height - 16);
        PageFormat format = new PageFormat();
        format.setPaper(paper);
        return format;
    }

    private void escribirPdf(Path path, List<String> lineas) throws IOException {
        int width = 227;
        int height = Math.max(280, lineas.size() * 12 + 50);
        StringBuilder content = new StringBuilder();
        content.append("BT\n");
        content.append("/F1 8 Tf\n");
        content.append("12 ").append(height - 24).append(" Td\n");
        for (String linea : lineas) {
            content.append("(").append(escapePdf(linea)).append(") Tj\n");
            content.append("0 -10 Td\n");
        }
        content.append("ET\n");

        byte[] contentBytes = content.toString().getBytes(StandardCharsets.ISO_8859_1);
        List<byte[]> objects = new ArrayList<byte[]>();
        objects.add(bytes("<< /Type /Catalog /Pages 2 0 R >>"));
        objects.add(bytes("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"));
        objects.add(bytes("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + width + " " + height
                + "] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>"));
        objects.add(bytes("<< /Type /Font /Subtype /Type1 /BaseFont /Courier >>"));
        objects.add(contenido(bytes("<< /Length " + contentBytes.length + " >>"), contentBytes));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(bytes("%PDF-1.4\n"));
        List<Integer> offsets = new ArrayList<Integer>();
        offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(out.size());
            out.write(bytes((i + 1) + " 0 obj\n"));
            out.write(objects.get(i));
            out.write(bytes("\nendobj\n"));
        }
        int xref = out.size();
        out.write(bytes("xref\n0 " + (objects.size() + 1) + "\n"));
        out.write(bytes("0000000000 65535 f \n"));
        for (int i = 1; i < offsets.size(); i++) {
            out.write(bytes(String.format(Locale.US, "%010d 00000 n \n", offsets.get(i))));
        }
        out.write(bytes("trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n"));
        out.write(bytes("startxref\n" + xref + "\n%%EOF"));
        Files.write(path, out.toByteArray());
    }

    private byte[] contenido(byte[] header, byte[] stream) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(header);
        out.write(bytes("\nstream\n"));
        out.write(stream);
        out.write(bytes("endstream"));
        return out.toByteArray();
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    private String escapePdf(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }

    private void agregarSiTiene(List<String> lineas, String value) {
        String limpio = value == null ? "" : value.trim();
        if (!limpio.endsWith(":") && !limpio.isEmpty()) {
            lineas.add(centrar(limpio));
        }
    }

    private String moneda(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.00", symbols);
        return "S/. " + df.format(value);
    }

    private String importeCorto(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.00", symbols);
        return df.format(value);
    }

    private String campo(String label, String value) {
        return String.format(Locale.US, "%-9s: %s", label, value);
    }

    private String centrar(String value) {
        String limpio = recortar(value, TICKET_WIDTH_CHARS);
        int espacios = Math.max(0, (TICKET_WIDTH_CHARS - limpio.length()) / 2);
        return repetir(" ", espacios) + limpio;
    }

    private String alinearDerecha(String value) {
        String limpio = recortar(value, TICKET_WIDTH_CHARS);
        int espacios = Math.max(0, TICKET_WIDTH_CHARS - limpio.length());
        return repetir(" ", espacios) + limpio;
    }

    private String recortar(String value, int max) {
        String limpio = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return limpio.length() <= max ? limpio : limpio.substring(0, max - 1) + ".";
    }

    private String repetir(String value, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            sb.append(value);
        }
        return sb.toString();
    }

    private String valor(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private String tituloComprobante(VentaTicket ticket) {
        if (VentaEstado.EN_PROCESO.equals(ticket.getEstado())) {
            return "COMPROBANTE INTERNO DE RESERVA";
        }
        return "COMPROBANTE INTERNO DE VENTA";
    }

    private String pago(String estado) {
        if (VentaEstado.EN_PROCESO.equals(VentaEstado.normalizar(estado))) {
            return "Pendiente";
        }
        if (VentaEstado.CANCELADA.equals(VentaEstado.normalizar(estado))) {
            return "Cancelado";
        }
        return "Contado";
    }

    private String mensajeFinal(String estado) {
        if (VentaEstado.EN_PROCESO.equals(VentaEstado.normalizar(estado))) {
            return "Reserva registrada";
        }
        if (VentaEstado.CANCELADA.equals(VentaEstado.normalizar(estado))) {
            return "Reserva cancelada";
        }
        return "Gracias por su compra";
    }
}

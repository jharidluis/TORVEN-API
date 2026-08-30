package vistas;

import java.awt.Component;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.SwingConstants;
import javax.swing.ButtonModel;
import javax.swing.border.Border;
import javax.swing.border.AbstractBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.plaf.basic.BasicButtonUI;

final class Ui {
    static final Color COLOR_FONDO = new Color(245, 247, 250);
    static final Color COLOR_TEXTO = new Color(17, 24, 39);
    static final Color COLOR_MUTED = new Color(91, 101, 117);
    static final Color COLOR_PRINCIPAL = new Color(17, 24, 39);
    static final Color COLOR_ACCENTO = new Color(20, 184, 166);
    static final Color COLOR_VERDE = new Color(22, 163, 74);
    static final Color COLOR_MORADO = new Color(124, 58, 237);
    static final Color COLOR_ALERTA = new Color(220, 38, 38);

    private Ui() {
    }

    static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    static void prepararTabla(JTable table) {
        table.setRowHeight(28);
        table.setAutoCreateRowSorter(false);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setShowVerticalLines(false);
        table.setGridColor(new Color(229, 231, 235));
        table.getTableHeader().setFont(table.getTableHeader().getFont().deriveFont(Font.BOLD));
        table.getTableHeader().setBackground(new Color(236, 242, 247));
        table.getTableHeader().setForeground(COLOR_TEXTO);
    }

    static void columnaDinero(JTable table, int columna) {
        table.getColumnModel().getColumn(columna).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof BigDecimal) {
                    setText("S/. " + dinero((BigDecimal) value));
                } else {
                    setText(value == null ? "" : String.valueOf(value));
                }
                setHorizontalAlignment(SwingConstants.RIGHT);
            }
        });
    }

    static void columnaDerecha(JTable table, int columna) {
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();
        renderer.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(columna).setCellRenderer(renderer);
    }

    static void anchoColumna(JTable table, int columna, int ancho) {
        TableColumn tableColumn = table.getColumnModel().getColumn(columna);
        tableColumn.setPreferredWidth(ancho);
    }

    static void ocultarColumna(JTable table, int columna) {
        TableColumn tableColumn = table.getColumnModel().getColumn(columna);
        tableColumn.setMinWidth(0);
        tableColumn.setPreferredWidth(0);
        tableColumn.setMaxWidth(0);
    }

    static void estilizarBotonPrimario(JButton button) {
        prepararBoton(button);
        button.setBackground(COLOR_PRINCIPAL);
        button.setForeground(Color.WHITE);
        button.setBorder(new CompoundBorder(
                new RoundedBorder(COLOR_PRINCIPAL, null, 12),
                new EmptyBorder(8, 16, 8, 16)));
    }

    static void estilizarBotonSecundario(JButton button) {
        prepararBoton(button);
        button.setBackground(Color.WHITE);
        button.setForeground(COLOR_PRINCIPAL);
        button.setBorder(new CompoundBorder(
                new RoundedBorder(new Color(194, 207, 220), null, 12),
                new EmptyBorder(8, 16, 8, 16)));
    }

    private static void prepararBoton(JButton button) {
        button.setUI(new RoundedButtonUI());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(true);
        button.setFocusPainted(false);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    static void tarjeta(JComponent component) {
        Border original = component.getBorder();
        Border interior = original == null ? new EmptyBorder(10, 10, 10, 10)
                : javax.swing.BorderFactory.createCompoundBorder(new EmptyBorder(6, 6, 6, 6), original);
        component.setBackground(Color.WHITE);
        component.setOpaque(false);
        component.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(218, 224, 232), Color.WHITE, 14),
                interior));
    }

    static void placaBlanca(JComponent component, int vertical, int horizontal) {
        component.setBackground(Color.WHITE);
        component.setOpaque(false);
        component.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                new RoundedBorder(new Color(229, 231, 235), Color.WHITE, 16),
                new EmptyBorder(vertical, horizontal, vertical, horizontal)));
    }

    static void alCambiarTexto(JTextField field, final Runnable action) {
        final Timer espera = new Timer(350, e -> action.run());
        espera.setRepeats(false);
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                espera.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                espera.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                espera.restart();
            }
        });
    }

    static void error(Component parent, Exception ex) {
        Mensajes.error(parent, ex);
    }

    static void info(Component parent, String message) {
        Mensajes.exito(parent, message);
    }

    static void aviso(Component parent, String message) {
        Mensajes.aviso(parent, message);
    }

    static boolean confirmar(Component parent, String message) {
        return Mensajes.confirmar(parent, message);
    }

    static BigDecimal leerDinero(String text) {
        String limpio = text == null ? "" : text.trim().replace("S/.", "").replace(",", ".");
        if (limpio.isEmpty()) {
            throw new NumberFormatException("Ingresa un precio.");
        }
        return new BigDecimal(limpio);
    }

    static String dinero(BigDecimal value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("0.00", symbols);
        return df.format(value);
    }

    static int leerEntero(String text, String campo) {
        String limpio = text == null ? "" : text.trim();
        if (limpio.isEmpty()) {
            throw new NumberFormatException("Ingresa " + campo + ".");
        }
        return Integer.parseInt(limpio);
    }

    private static class RoundedButtonUI extends BasicButtonUI {
        @Override
        public void paint(Graphics graphics, JComponent component) {
            AbstractButton button = (AbstractButton) component;
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel model = button.getModel();
                Color base = button.isEnabled() ? button.getBackground() : new Color(229, 231, 235);
                if (model.isPressed()) {
                    base = oscurecer(base, 0.92f);
                } else if (model.isRollover()) {
                    base = aclarar(base, 1.04f);
                }
                g.setColor(base);
                g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 12, 12);
            } finally {
                g.dispose();
            }
            super.paint(graphics, component);
        }

        private Color oscurecer(Color color, float factor) {
            return new Color(
                    Math.max(0, Math.round(color.getRed() * factor)),
                    Math.max(0, Math.round(color.getGreen() * factor)),
                    Math.max(0, Math.round(color.getBlue() * factor)));
        }

        private Color aclarar(Color color, float factor) {
            return new Color(
                    Math.min(255, Math.round(color.getRed() * factor)),
                    Math.min(255, Math.round(color.getGreen() * factor)),
                    Math.min(255, Math.round(color.getBlue() * factor)));
        }
    }

    private static class RoundedBorder extends AbstractBorder {
        private final Color line;
        private final Color fill;
        private final int radius;

        RoundedBorder(Color line, Color fill, int radius) {
            this.line = line;
            this.fill = fill;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (fill != null) {
                    g.setColor(fill);
                    g.fillRoundRect(x, y, width - 1, height - 1, radius, radius);
                }
                if (line != null) {
                    g.setColor(line);
                    g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
                }
            } finally {
                g.dispose();
            }
        }
    }
}

package com.santlaza.gastosbot.bot;

import com.santlaza.gastosbot.gasto.Gasto;
import com.santlaza.gastosbot.gasto.GastoService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

// Recibe el texto que escribió el usuario y devuelve la respuesta del bot.
// No sabe nada de Telegram: así se puede testear sin conectarse a nada.
@Component
public class ComandoHandler {

    static final String AYUDA = """
            Hola! Soy tu bot de gastos. Comandos:

            /gasto <monto> [categoría] [descripción]
               Ej: /gasto 1500 comida almuerzo con Juan
               (también podés escribir directo: 1500 comida almuerzo)
            /lista  → gastos del mes con su número (#)
            /mes  → total del mes y por categoría
            /editar <#> <monto> [categoría] [descripción]
               Usá - para no cambiar un dato. Ej: /editar 3 - transporte
            /borrar <#>
            /ayuda  → este mensaje""";

    private static final int MAX_GASTOS_EN_LISTA = 30;   // Telegram corta los mensajes de más de 4096 caracteres
    private static final int MAX_LARGO_TEXTO = 255;      // largo de las columnas en la base
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("10000000000"); // la columna tiene 10 dígitos enteros
    private static final String NO_CAMBIAR = "-";

    private static final Locale ARGENTINA = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter FORMATO_MES = DateTimeFormatter.ofPattern("MMMM yyyy", ARGENTINA);

    private final GastoService gastos;

    public ComandoHandler(GastoService gastos) {
        this.gastos = gastos;
    }

    public String responder(Long chatId, String texto) {
        if (texto == null || texto.isBlank()) {
            return AYUDA;
        }

        // "/gasto 1500 comida" -> ["/gasto", "1500", "comida"]
        String[] partes = texto.trim().split("\\s+");
        String comando = partes[0].toLowerCase();
        List<String> args = Arrays.asList(partes).subList(1, partes.length);

        // En grupos Telegram manda "/gasto@NombreDelBot": nos quedamos con "/gasto"
        if (comando.contains("@")) {
            comando = comando.substring(0, comando.indexOf('@'));
        }

        try {
            return switch (comando) {
                case "/gasto" -> registrar(chatId, args);
                case "/lista" -> lista(chatId);
                case "/mes" -> resumenDelMes(chatId);
                case "/editar" -> editar(chatId, args);
                case "/borrar" -> borrar(chatId, args);
                case "/start", "/ayuda", "/help" -> AYUDA;
                default -> {
                    if (comando.startsWith("/")) {
                        yield "No conozco ese comando.\n\n" + AYUDA;
                    }
                    // Texto sin "/" que empieza con un número ("1500 comida"): lo tomamos como un gasto
                    if (Character.isDigit(comando.charAt(0)) || comando.startsWith("$")) {
                        yield registrar(chatId, Arrays.asList(partes));
                    }
                    yield "No te entendí.\n\n" + AYUDA;
                }
            };
        } catch (IllegalArgumentException | NoSuchElementException e) {
            // Errores esperables (monto inválido, gasto que no existe...): le mostramos el motivo al usuario.
            // NumberFormatException es una IllegalArgumentException, así que también cae acá.
            return "⚠️ " + e.getMessage();
        }
    }

    // ---------- Comandos ----------

    private String registrar(Long chatId, List<String> args) {
        if (args.isEmpty()) {
            return "Decime el monto. Ej: /gasto 1500 comida almuerzo";
        }
        BigDecimal monto = parsearMonto(args.get(0));
        String categoria = args.size() > 1 ? args.get(1) : null;
        String descripcion = unir(args, 2);

        Gasto g = gastos.registrar(chatId, monto, validarLargo(descripcion), validarLargo(categoria), null);
        return "✅ Anotado #" + g.getId() + ": " + formatear(g.getMonto()) + " en " + g.getCategoria()
                + (g.getDescripcion() != null ? " (" + g.getDescripcion() + ")" : "");
    }

    private String lista(Long chatId) {
        List<Gasto> delMes = gastos.listarDelMes(chatId);
        if (delMes.isEmpty()) {
            return "No tenés gastos este mes. Anotá uno con /gasto 1500 comida";
        }

        StringBuilder sb = new StringBuilder("Gastos de " + YearMonth.now().format(FORMATO_MES) + ":\n");
        delMes.stream().limit(MAX_GASTOS_EN_LISTA).forEach(g -> sb
                .append("\n#").append(g.getId())
                .append(" · ").append(g.getFecha().format(FORMATO_FECHA))
                .append(" · ").append(formatear(g.getMonto()))
                .append(" · ").append(g.getCategoria())
                .append(g.getDescripcion() != null ? " · " + recortar(g.getDescripcion(), 40) : ""));

        if (delMes.size() > MAX_GASTOS_EN_LISTA) {
            sb.append("\n\n... y ").append(delMes.size() - MAX_GASTOS_EN_LISTA).append(" más");
        }
        return sb.toString();
    }

    private String resumenDelMes(Long chatId) {
        Map<String, BigDecimal> porCategoria = gastos.totalesPorCategoria(chatId);
        if (porCategoria.isEmpty()) {
            return "No tenés gastos este mes.";
        }

        StringBuilder sb = new StringBuilder("Resumen de " + YearMonth.now().format(FORMATO_MES) + ":\n");
        porCategoria.forEach((categoria, total) ->
                sb.append("\n• ").append(categoria).append(": ").append(formatear(total)));
        sb.append("\n\nTotal: ").append(formatear(gastos.totalDelMes(chatId)));
        return sb.toString();
    }

    private String editar(Long chatId, List<String> args) {
        if (args.size() < 2) {
            return "Uso: /editar <#> <monto> [categoría] [descripción]\nUsá - para no cambiar un dato. Ej: /editar 3 - transporte";
        }
        Long id = parsearId(args.get(0));
        BigDecimal monto = NO_CAMBIAR.equals(args.get(1)) ? null : parsearMonto(args.get(1));
        String categoria = (args.size() > 2 && !NO_CAMBIAR.equals(args.get(2))) ? args.get(2) : null;
        String descripcion = unir(args, 3);

        Gasto g = gastos.editar(chatId, id, monto, validarLargo(descripcion), validarLargo(categoria));
        return "✏️ Editado #" + g.getId() + ": " + formatear(g.getMonto()) + " en " + g.getCategoria()
                + (g.getDescripcion() != null ? " (" + g.getDescripcion() + ")" : "");
    }

    private String borrar(Long chatId, List<String> args) {
        if (args.isEmpty()) {
            return "Uso: /borrar <#>. Mirá los números con /lista";
        }
        Long id = parsearId(args.get(0));
        gastos.eliminar(chatId, id);
        return "🗑️ Borrado el gasto #" + id;
    }

    // ---------- Auxiliares ----------

    // Acepta "1500", "$1500", "1500,50", "1500.50", "1.500", "1.500,50" y "1,500.50"
    static BigDecimal parsearMonto(String texto) {
        String t = texto.replace("$", "");
        int ultimaComa = t.lastIndexOf(',');
        int ultimoPunto = t.lastIndexOf('.');

        if (ultimaComa >= 0 && ultimoPunto >= 0) {
            // Hay de los dos: el que aparece último es el separador decimal
            t = (ultimaComa > ultimoPunto)
                    ? t.replace(".", "").replace(',', '.')   // 1.500,50
                    : t.replace(",", "");                    // 1,500.50
        } else if (ultimaComa >= 0) {
            t = t.replace(',', '.');                         // 1500,50
        } else if (t.matches("\\d{1,3}(\\.\\d{3})+")) {
            t = t.replace(".", "");                          // 1.500 o 1.500.000 (puntos de miles)
        }

        BigDecimal monto;
        try {
            monto = new BigDecimal(t);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + texto + "\" no es un monto válido. Ej: 1500 o 1500,50");
        }
        if (monto.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("El monto puede tener hasta 2 decimales");
        }
        if (monto.compareTo(MONTO_MAXIMO) >= 0) {
            throw new IllegalArgumentException("El monto es demasiado grande");
        }
        return monto;
    }

    private static Long parsearId(String texto) {
        try {
            return Long.parseLong(texto.replace("#", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("\"" + texto + "\" no es un número de gasto. Mirá los números con /lista");
        }
    }

    // Une las palabras desde la posición "desde" en adelante; null si no hay ninguna
    private static String unir(List<String> args, int desde) {
        return args.size() > desde ? String.join(" ", args.subList(desde, args.size())) : null;
    }

    private static String validarLargo(String texto) {
        if (texto != null && texto.length() > MAX_LARGO_TEXTO) {
            throw new IllegalArgumentException("El texto es muy largo (máximo " + MAX_LARGO_TEXTO + " caracteres)");
        }
        return texto;
    }

    private static String recortar(String texto, int largo) {
        return texto.length() <= largo ? texto : texto.substring(0, largo - 1) + "…";
    }

    // 1500 -> "$1.500"   1500.5 -> "$1.500,50"
    static String formatear(BigDecimal monto) {
        String patron = monto.stripTrailingZeros().scale() > 0 ? "$#,##0.00" : "$#,##0";
        return new DecimalFormat(patron, DecimalFormatSymbols.getInstance(ARGENTINA)).format(monto);
    }
}

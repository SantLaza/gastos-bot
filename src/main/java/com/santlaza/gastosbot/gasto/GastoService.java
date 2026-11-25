package com.santlaza.gastosbot.gasto;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true) // por defecto los métodos solo leen; los que escriben lo pisan abajo
public class GastoService {

    private static final String CATEGORIA_POR_DEFECTO = "otros";

    private final GastoRepository repo;

    public GastoService(GastoRepository repo) { // Spring inyecta el repository solo
        this.repo = repo;
    }

    // ---------- Crear ----------

    @Transactional
    public Gasto registrar(Long chatId, BigDecimal monto, String descripcion, String categoria, LocalDate fecha) {
        validarMonto(monto);
        LocalDate f = (fecha != null) ? fecha : LocalDate.now();
        Gasto gasto = new Gasto(chatId, monto, limpiar(descripcion), normalizarCategoria(categoria), f);
        return repo.save(gasto);
    }

    // ---------- Leer ----------

    public List<Gasto> listar(Long chatId) {
        return repo.findByChatIdOrderByFechaDesc(chatId);
    }

    public List<Gasto> listarDelMes(Long chatId, YearMonth mes) {
        return repo.findByChatIdAndFechaBetweenOrderByFechaDesc(chatId, mes.atDay(1), mes.atEndOfMonth());
    }

    public List<Gasto> listarDelMes(Long chatId) {
        return listarDelMes(chatId, YearMonth.now());
    }

    public BigDecimal totalDelMes(Long chatId, YearMonth mes) {
        return listarDelMes(chatId, mes).stream()
                .map(Gasto::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public BigDecimal totalDelMes(Long chatId) {
        return totalDelMes(chatId, YearMonth.now());
    }

    // Ej: {"comida": 15000, "transporte": 4200}, ordenado alfabéticamente
    public Map<String, BigDecimal> totalesPorCategoria(Long chatId, YearMonth mes) {
        Map<String, BigDecimal> totales = new TreeMap<>();
        for (Gasto g : listarDelMes(chatId, mes)) {
            totales.merge(g.getCategoria(), g.getMonto(), BigDecimal::add);
        }
        return totales;
    }

    public Map<String, BigDecimal> totalesPorCategoria(Long chatId) {
        return totalesPorCategoria(chatId, YearMonth.now());
    }

    // ---------- Editar ----------

    // Los parámetros en null significan "no cambiar ese dato"
    @Transactional
    public Gasto editar(Long chatId, Long id, BigDecimal monto, String descripcion, String categoria) {
        Gasto gasto = buscar(chatId, id);

        BigDecimal nuevoMonto = (monto != null) ? monto : gasto.getMonto();
        validarMonto(nuevoMonto);
        String nuevaDescripcion = (descripcion != null) ? limpiar(descripcion) : gasto.getDescripcion();
        String nuevaCategoria = (categoria != null) ? normalizarCategoria(categoria) : gasto.getCategoria();

        gasto.actualizar(nuevoMonto, nuevaDescripcion, nuevaCategoria);
        // No hace falta repo.save(): dentro de la transacción JPA detecta el cambio y lo guarda al terminar
        return gasto;
    }

    // ---------- Borrar ----------

    @Transactional
    public void eliminar(Long chatId, Long id) {
        repo.delete(buscar(chatId, id));
    }

    // ---------- Auxiliares ----------

    // Solo encuentra el gasto si es de ese chat: así nadie edita o borra gastos ajenos
    private Gasto buscar(Long chatId, Long id) {
        return repo.findByIdAndChatId(id, chatId)
                .orElseThrow(() -> new NoSuchElementException("No existe el gasto #" + id));
    }

    private void validarMonto(BigDecimal monto) {
        if (monto == null || monto.signum() <= 0) {
            throw new IllegalArgumentException("El monto tiene que ser mayor a 0");
        }
    }

    // "  Comida " -> "comida"; vacío o null -> "otros"
    private String normalizarCategoria(String categoria) {
        String c = limpiar(categoria);
        return (c == null) ? CATEGORIA_POR_DEFECTO : c.toLowerCase();
    }

    // Saca espacios de los costados; si queda vacío devuelve null
    private String limpiar(String texto) {
        if (texto == null) return null;
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }
}

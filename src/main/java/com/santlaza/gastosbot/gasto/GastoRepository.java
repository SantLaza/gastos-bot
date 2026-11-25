package com.santlaza.gastosbot.gasto;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GastoRepository extends JpaRepository<Gasto, Long> {

    // Todos los gastos de un chat, del más reciente al más viejo
    List<Gasto> findByChatIdOrderByFechaDesc(Long chatId);

    // Gastos de un chat en un rango de fechas (ej: el mes actual)
    List<Gasto> findByChatIdAndFechaBetweenOrderByFechaDesc(Long chatId, LocalDate desde, LocalDate hasta);

    // Busca un gasto solo si pertenece a ese chat (para que nadie edite/borre gastos ajenos)
    Optional<Gasto> findByIdAndChatId(Long id, Long chatId);
}

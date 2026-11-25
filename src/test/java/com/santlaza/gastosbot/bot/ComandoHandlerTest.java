package com.santlaza.gastosbot.bot;

import com.santlaza.gastosbot.gasto.GastoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// @DataJpaTest levanta solo la parte de base de datos (H2 en memoria) y deshace los cambios después de cada test.
// Con @Import sumamos el service y el handler reales: se prueba todo junto, sin Telegram.
@DataJpaTest
@Import({GastoService.class, ComandoHandler.class})
class ComandoHandlerTest {

    private static final Long CHAT = 111L;
    private static final Long OTRO_CHAT = 222L;

    @Autowired
    private ComandoHandler handler;

    @Test
    void registraUnGastoConCategoriaYDescripcion() {
        String respuesta = handler.responder(CHAT, "/gasto 1500 Comida almuerzo con Juan");

        assertThat(respuesta).startsWith("✅ Anotado #").contains("$1.500", "comida", "almuerzo con Juan");
    }

    @Test
    void sinCategoriaQuedaEnOtros() {
        assertThat(handler.responder(CHAT, "/gasto 200")).contains("en otros");
    }

    @Test
    void textoQueEmpiezaConNumeroSeTomaComoGasto() {
        assertThat(handler.responder(CHAT, "350,50 cafe")).startsWith("✅").contains("$350,50", "cafe");
    }

    @Test
    void entiendeComandosConElNombreDelBot() {
        // En grupos Telegram manda "/gasto@NombreDelBot"
        assertThat(handler.responder(CHAT, "/gasto@MiGastosBot 100 kiosco")).startsWith("✅");
    }

    @Test
    void montoInvalidoDevuelveUnAviso() {
        assertThat(handler.responder(CHAT, "/gasto mucho comida")).startsWith("⚠️").contains("no es un monto válido");
        assertThat(handler.responder(CHAT, "/gasto 0")).startsWith("⚠️").contains("mayor a 0");
        assertThat(handler.responder(CHAT, "/gasto")).contains("Decime el monto");
    }

    @Test
    void listaMuestraLosGastosConSuNumero() {
        handler.responder(CHAT, "/gasto 1500 comida");
        handler.responder(CHAT, "/gasto 800 transporte sube");

        String lista = handler.responder(CHAT, "/lista");

        assertThat(lista).contains("#", "$1.500", "comida", "$800", "transporte", "sube");
    }

    @Test
    void listaVaciaAvisa() {
        assertThat(handler.responder(CHAT, "/lista")).contains("No tenés gastos este mes");
    }

    @Test
    void mesMuestraTotalPorCategoriaYGeneral() {
        handler.responder(CHAT, "/gasto 1000 comida");
        handler.responder(CHAT, "/gasto 500 comida");
        handler.responder(CHAT, "/gasto 300 transporte");

        String resumen = handler.responder(CHAT, "/mes");

        assertThat(resumen).contains("comida: $1.500", "transporte: $300", "Total: $1.800");
    }

    @Test
    void editarConGuionNoCambiaEseDato() {
        Long id = idDe(handler.responder(CHAT, "/gasto 1500 comida almuerzo"));

        String respuesta = handler.responder(CHAT, "/editar " + id + " - transporte");

        assertThat(respuesta).startsWith("✏️").contains("$1.500", "transporte", "almuerzo");
    }

    @Test
    void borraUnGasto() {
        Long id = idDe(handler.responder(CHAT, "/gasto 1500 comida"));

        assertThat(handler.responder(CHAT, "/borrar " + id)).contains("Borrado el gasto #" + id);
        assertThat(handler.responder(CHAT, "/lista")).contains("No tenés gastos este mes");
    }

    @Test
    void noSePuedeTocarElGastoDeOtroChat() {
        Long id = idDe(handler.responder(CHAT, "/gasto 1500 comida"));

        assertThat(handler.responder(OTRO_CHAT, "/borrar " + id)).startsWith("⚠️").contains("No existe el gasto");
        assertThat(handler.responder(OTRO_CHAT, "/editar " + id + " 1")).startsWith("⚠️");
        assertThat(handler.responder(CHAT, "/lista")).contains("$1.500"); // sigue estando
    }

    @Test
    void idInvalidoDevuelveUnAviso() {
        assertThat(handler.responder(CHAT, "/borrar abc")).startsWith("⚠️").contains("no es un número de gasto");
    }

    @Test
    void comandoDesconocidoOTextoSinSentidoMuestraLaAyuda() {
        assertThat(handler.responder(CHAT, "/cualquiera")).contains("No conozco ese comando", "/gasto");
        assertThat(handler.responder(CHAT, "hola")).contains("No te entendí", "/gasto");
        assertThat(handler.responder(CHAT, "/start")).isEqualTo(ComandoHandler.AYUDA);
    }

    @Test
    void parseaMontosEnDistintosFormatos() {
        assertThat(ComandoHandler.parsearMonto("1500")).isEqualByComparingTo("1500");
        assertThat(ComandoHandler.parsearMonto("$1500")).isEqualByComparingTo("1500");
        assertThat(ComandoHandler.parsearMonto("1500,50")).isEqualByComparingTo("1500.50");
        assertThat(ComandoHandler.parsearMonto("1500.50")).isEqualByComparingTo("1500.50");
        assertThat(ComandoHandler.parsearMonto("1.500")).isEqualByComparingTo("1500");
        assertThat(ComandoHandler.parsearMonto("1.500.000")).isEqualByComparingTo("1500000");
        assertThat(ComandoHandler.parsearMonto("1.500,50")).isEqualByComparingTo("1500.50");
        assertThat(ComandoHandler.parsearMonto("1,500.50")).isEqualByComparingTo("1500.50");
    }

    @Test
    void rechazaMontosConMasDeDosDecimalesODemasiadoGrandes() {
        assertThatThrownBy(() -> ComandoHandler.parsearMonto("10,555")).hasMessageContaining("2 decimales");
        assertThatThrownBy(() -> ComandoHandler.parsearMonto("99999999999")).hasMessageContaining("demasiado grande");
    }

    @Test
    void formateaMontosAlEstiloArgentino() {
        assertThat(ComandoHandler.formatear(new BigDecimal("1500"))).isEqualTo("$1.500");
        assertThat(ComandoHandler.formatear(new BigDecimal("1500.5"))).isEqualTo("$1.500,50");
    }

    // "✅ Anotado #7: ..." -> 7
    private static Long idDe(String respuesta) {
        return Long.parseLong(respuesta.replaceAll("^.*?#(\\d+).*$", "$1").trim());
    }
}

package com.santlaza.gastosbot.bot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Prueba la parte de Telegram con mensajes JSON como los que manda Telegram de verdad.
// De paso confirma que la librería puede leer esos JSON con las versiones de Jackson del proyecto.
class GastosBotTest {

    private final ObjectMapper json = new ObjectMapper();

    // /ayuda no usa la base, así que alcanza con un handler sin service
    private final GastosBot bot = new GastosBot(null, new ComandoHandler(null));

    @Test
    void contestaLosMensajesDeTextoAlMismoChat() throws Exception {
        Update update = json.readValue("""
                {"update_id": 1,
                 "message": {"message_id": 10, "date": 1764000000,
                             "chat": {"id": 12345, "type": "private"},
                             "text": "/ayuda"}}
                """, Update.class);

        Optional<SendMessage> respuesta = bot.respuestaPara(update);

        assertThat(respuesta).isPresent();
        assertThat(respuesta.get().getChatId()).isEqualTo("12345");
        assertThat(respuesta.get().getText()).isEqualTo(ComandoHandler.AYUDA);
    }

    @Test
    void ignoraLoQueNoEsTexto() throws Exception {
        Update sticker = json.readValue("""
                {"update_id": 2,
                 "message": {"message_id": 11, "date": 1764000000,
                             "chat": {"id": 12345, "type": "private"},
                             "sticker": {"file_id": "abc", "file_unique_id": "def", "type": "regular",
                                         "width": 512, "height": 512, "is_animated": false, "is_video": false}}}
                """, Update.class);
        Update sinMensaje = json.readValue("{\"update_id\": 3}", Update.class);

        assertThat(bot.respuestaPara(sticker)).isEmpty();
        assertThat(bot.respuestaPara(sinMensaje)).isEmpty();
    }

    @Test
    void unErrorInesperadoNoTumbaElBot() throws Exception {
        // Con service null, /lista explota con NullPointerException: el bot tiene que contestar igual
        Update update = json.readValue("""
                {"update_id": 4,
                 "message": {"message_id": 12, "date": 1764000000,
                             "chat": {"id": 12345, "type": "private"},
                             "text": "/lista"}}
                """, Update.class);

        assertThat(bot.respuestaPara(update)).get()
                .extracting(SendMessage::getText).asString().contains("Algo salió mal");
    }
}

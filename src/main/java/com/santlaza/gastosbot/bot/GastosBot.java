package com.santlaza.gastosbot.bot;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Optional;

// Recibe los mensajes que llegan de Telegram y contesta.
// La lógica de los comandos está en ComandoHandler; esta clase solo traduce entre Telegram y el handler.
// No lleva @Component: se crea en TelegramConfig solo si hay token.
public class GastosBot implements LongPollingSingleThreadUpdateConsumer {

    private static final Logger log = LoggerFactory.getLogger(GastosBot.class);

    private final TelegramClient telegram;
    private final ComandoHandler handler;

    public GastosBot(TelegramClient telegram, ComandoHandler handler) {
        this.telegram = telegram;
        this.handler = handler;
    }

    // La librería llama a este método por cada novedad (mensaje, sticker, edición, etc.)
    @Override
    public void consume(Update update) {
        respuestaPara(update).ifPresent(respuesta -> {
            try {
                telegram.execute(respuesta);
            } catch (TelegramApiException e) {
                log.error("No se pudo enviar la respuesta a Telegram", e);
            }
        });
    }

    // Arma la respuesta sin enviarla (separado de consume() para poder testearlo sin Telegram)
    Optional<SendMessage> respuestaPara(Update update) {
        // Solo nos interesan los mensajes de texto: stickers, fotos, ediciones, etc. se ignoran
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return Optional.empty();
        }

        Long chatId = update.getMessage().getChatId();
        String texto;
        try {
            texto = handler.responder(chatId, update.getMessage().getText());
        } catch (RuntimeException e) {
            // Un error inesperado no tiene que tumbar el bot: lo registramos y avisamos al usuario
            log.error("Error procesando el mensaje del chat {}", chatId, e);
            texto = "😵 Algo salió mal procesando tu mensaje. Probá de nuevo.";
        }
        return Optional.of(new SendMessage(chatId.toString(), texto));
    }
}

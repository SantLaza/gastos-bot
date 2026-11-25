package com.santlaza.gastosbot.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

// Conecta el bot con Telegram. Toda esta clase se ignora si no hay token
// (por ejemplo en los tests), así la app arranca igual sin conectarse a nada.
@Configuration
@ConditionalOnExpression("'${telegram.bot.token:}' != ''")
public class TelegramConfig {

    private final String token;

    public TelegramConfig(@Value("${telegram.bot.token}") String token) {
        this.token = token;
    }

    // Cliente para ENVIAR mensajes a Telegram
    @Bean
    public TelegramClient telegramClient() {
        return new OkHttpTelegramClient(token);
    }

    @Bean
    public GastosBot gastosBot(TelegramClient telegramClient, ComandoHandler handler) {
        return new GastosBot(telegramClient, handler);
    }

    // RECIBE mensajes con "long polling": le pregunta a Telegram cada tanto si hay novedades.
    // Al registrar el bot empieza a escuchar; destroyMethod lo frena cuando se apaga la app.
    @Bean(destroyMethod = "close")
    public TelegramBotsLongPollingApplication telegramBotsApplication(GastosBot bot) {
        TelegramBotsLongPollingApplication app = new TelegramBotsLongPollingApplication();
        try {
            app.registerBot(token, bot);
        } catch (TelegramApiException e) {
            // El error de la librería viene casi vacío (solo "401"), así que lo explicamos mejor.
            // El error original queda abajo en el log como "Caused by".
            throw new IllegalStateException("No se pudo conectar el bot a Telegram. Revisá que el TELEGRAM_BOT_TOKEN sea correcto y que haya internet.", e);
        }
        return app;
    }
}

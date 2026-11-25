package com.santlaza.gastosbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Token vacío a propósito: aunque tengas TELEGRAM_BOT_TOKEN en tu máquina, el test no arranca el bot real
@SpringBootTest(properties = "telegram.bot.token=")
class GastosBotApplicationTests {

	@Test
	void contextLoads() {
	}

}

package co.texerp.integrations;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TexerpPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(
				TexerpPlatformApplication.class,
				args
		);
	}
}
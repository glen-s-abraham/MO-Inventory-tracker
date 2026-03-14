package in.mariasorganics.inventory_tracker;

import in.mariasorganics.inventory_tracker.service.ConfigService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class InventoryTrackerApplication {

	public static void main(String[] args) {
		SpringApplication.run(InventoryTrackerApplication.class, args);
	}

	@Bean
	CommandLineRunner init(ConfigService configService) {
		return args -> {
			configService.initializeDefaults();
		};
	}
}

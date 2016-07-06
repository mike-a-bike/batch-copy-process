package ch.abraxas.ecmip.tools.batch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BatchCopierApplication {

	public static void main(String[] args) {
		SpringApplication.run(BatchCopierApplication.class, args);
	}

}

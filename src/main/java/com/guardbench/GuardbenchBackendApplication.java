package com.guardbench;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.guardbench.testrun.infrastructure.evaluator.EvaluatorCatalogProperties;
import com.guardbench.testrun.infrastructure.persistence.ClaimProperties;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableConfigurationProperties({EvaluatorCatalogProperties.class, ClaimProperties.class})
@SpringBootApplication
public class GuardbenchBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(GuardbenchBackendApplication.class, args);
	}

}

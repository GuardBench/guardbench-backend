package com.guardbench;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.guardbench.testsupport.PostgresTestConfiguration;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class GuardbenchBackendApplicationTests {

	@Test
	@DisplayName("PostgreSQL Testcontainers를 사용해 애플리케이션 context를 로드한다")
	void contextLoads() {
	}

}

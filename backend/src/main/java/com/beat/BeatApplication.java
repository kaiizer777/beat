package com.beat;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URI;

@SpringBootApplication
public class BeatApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> {
			if (System.getProperty(entry.getKey()) == null) {
				System.setProperty(entry.getKey(), entry.getValue());
			}
		});

		String dbUrl = System.getProperty("DATABASE_URL");
		if (dbUrl != null) {
			try {
				String rawUrl = dbUrl.startsWith("jdbc:") ? dbUrl.substring(5) : dbUrl;
				URI uri = new URI(rawUrl);
				if (uri.getUserInfo() != null) {
					String[] userInfo = uri.getUserInfo().split(":", 2);
					if (System.getProperty("spring.datasource.username") == null) {
						System.setProperty("spring.datasource.username", userInfo[0]);
					}
					if (userInfo.length > 1 && System.getProperty("spring.datasource.password") == null) {
						System.setProperty("spring.datasource.password", userInfo[1]);
					}
					int port = uri.getPort() == -1 ? 5432 : uri.getPort();
					String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
					if (uri.getQuery() != null) {
						jdbcUrl += "?" + uri.getQuery();
					}
					System.setProperty("SPRING_DATASOURCE_URL", jdbcUrl);
				} else {
					System.setProperty("SPRING_DATASOURCE_URL", dbUrl.startsWith("jdbc:") ? dbUrl : "jdbc:" + dbUrl);
				}
			} catch (Exception e) {
				System.setProperty("SPRING_DATASOURCE_URL", dbUrl.startsWith("jdbc:") ? dbUrl : "jdbc:" + dbUrl);
			}
		}

		SpringApplication.run(BeatApplication.class, args);
	}

}


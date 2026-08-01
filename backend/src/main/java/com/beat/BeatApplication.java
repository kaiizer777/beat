package com.beat;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URI;

@SpringBootApplication
public class BeatApplication {

	static {
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
					if (System.getProperty("SPRING_DATASOURCE_USERNAME") == null) {
						System.setProperty("SPRING_DATASOURCE_USERNAME", userInfo[0]);
					}
					if (userInfo.length > 1 && System.getProperty("SPRING_DATASOURCE_PASSWORD") == null) {
						System.setProperty("SPRING_DATASOURCE_PASSWORD", userInfo[1]);
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
	}

	public static void main(String[] args) {
		SpringApplication.run(BeatApplication.class, args);
	}

}


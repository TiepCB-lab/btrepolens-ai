package com.btrepolens;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BtrepolensApplication {

	public static void main(String[] args) {
		// Use a PostgreSQL-compatible zone regardless of the host's timezone aliases.
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
		SpringApplication.run(BtrepolensApplication.class, args);
	}

}

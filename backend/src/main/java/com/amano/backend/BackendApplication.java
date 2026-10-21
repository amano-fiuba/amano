package com.amano.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.amano.backend.passwordreset.PasswordResetProperties;
import com.amano.backend.verification.VerificationProperties;

// D1: verification emails are sent from an @Async method so no request waits on SMTP.
// D6: unverified accounts older than 3 days are removed by an hourly @Scheduled job.
@EnableAsync
@EnableScheduling
// Binds verification.* deadlines (application.properties, task T004) and the
// password-reset.* deadline (feature 006) as beans.
@EnableConfigurationProperties({ VerificationProperties.class, PasswordResetProperties.class })
@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}

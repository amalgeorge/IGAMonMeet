package com.igamonmeet;

import java.util.TimeZone;
import javax.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Deployed as a WAR to an external Tomcat 9; also runnable with {@code mvn spring-boot:run}. */
@SpringBootApplication
@EnableScheduling
public class IgaMonMeetApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(IgaMonMeetApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(IgaMonMeetApplication.class);
    }

    /** All times are stored and processed in UTC; the UI displays Asia/Singapore. */
    @PostConstruct
    void useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }
}

package iwo.wintech.ngnfincalc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;

import java.time.Clock;
import java.time.ZoneOffset;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NGNFinancialCalcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NGNFinancialCalcApplication.class, args);
    }

    @Bean
    public Clock clock() {
        return Clock.system(ZoneOffset.UTC);
    }

}

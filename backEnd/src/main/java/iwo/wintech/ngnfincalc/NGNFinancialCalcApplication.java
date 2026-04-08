package iwo.wintech.ngnfincalc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NGNFinancialCalcApplication {

    public static void main(String[] args) {
        SpringApplication.run(NGNFinancialCalcApplication.class, args);
    }

}

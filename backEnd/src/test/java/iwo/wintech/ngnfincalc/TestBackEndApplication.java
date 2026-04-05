package iwo.wintech.ngnfincalc;

import org.springframework.boot.SpringApplication;

public class TestBackEndApplication {

    public static void main(String[] args) {
        SpringApplication.from(NGNFinancialCalcApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

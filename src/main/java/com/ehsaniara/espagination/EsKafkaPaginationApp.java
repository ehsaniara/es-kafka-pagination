package com.ehsaniara.espagination;

import com.ehsaniara.espagination.binder.PaginationBinder;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;

/**
 * Jay Ehsaniara
 * https://github.com/ehsaniara
 */
@EnableBinding({PaginationBinder.class})
@SpringBootApplication
public class EsKafkaPaginationApp {

    public static void main(String[] args) {
        SpringApplication.run(EsKafkaPaginationApp.class, args);
    }

}

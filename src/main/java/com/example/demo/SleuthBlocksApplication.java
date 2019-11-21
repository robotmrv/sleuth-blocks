package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.blockhound.BlockHound;
import reactor.blockhound.BlockingMethod;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@SpringBootApplication
public class SleuthBlocksApplication {

    public static void main(String[] args) {
        installAgents();
        SpringApplication.run(SleuthBlocksApplication.class, args);
    }

    private static void installAgents() {
        boolean enableBlockHound  =
                Boolean.parseBoolean(System.getProperty("reactor.tools.BlockHound.enabled", "true"));

        if (!enableBlockHound) {
            return;
        }

        BlockHound.install(
                builder -> builder
                        .allowBlockingCallsInside(SleuthBlocksApplication.class.getName(), "logBlocking")
                        .blockingMethodCallback(SleuthBlocksApplication::logBlocking)
        );

        Mono.delay(Duration.ofMillis(1))
                .doOnNext(it -> {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        throw Exceptions.propagate(e);
                    }
                })
                .subscribe(); // test log
    }

    private static void logBlocking(BlockingMethod blockingMethod) {
        System.err.println("Blocking Call inside: " + blockingMethod + " in Thread: " + Thread.currentThread());
        new Error().printStackTrace();
    }
}

@RequestMapping("/test")
@RestController()
class Ctrl {

    private static final Logger log = LoggerFactory.getLogger(Ctrl.class);

    private WebClient wc;

    public Ctrl(WebClient.Builder wcb, @Value("${server.port:8080}") int port) {
        this.wc = wcb.baseUrl("http://localhost:" + port).build();
    }

    @GetMapping
    public Mono<?> test() {
        return Flux.range(0, 1000)
                .publishOn(Schedulers.parallel())
                .flatMap(it -> getRequest(it + ""), 500)
                .then()
                .thenReturn("ok");
    }

    private Mono<String> getRequest(String id) {
        return wc.get()
                .uri("/test/delay/" + id)
                .retrieve()
                .bodyToMono(String.class);
    }

    @GetMapping("/delay/{id}")
    public Mono<?> delay(@PathVariable String id) {
        return Mono.delay(Duration.ofMillis(100))
                .doOnNext(aLong -> log.info("complete delay id: {}", id));
    }

}

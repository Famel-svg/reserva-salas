package com.famelsvg.reservasalas.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ConcurrentReservationIT extends AbstractPostgresIT {

    private static final int THREADS = 20;

    @Test
    void twentyConcurrentRequestsForTheSameSlotYieldExactlyOneCreated() throws Exception {
        UUID room = createRoom(newAdmin());
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            tokens.add(newMember()); // um usuario por thread: o limite de 3 reservas nao interfere
        }

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (String token : tokens) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return reserve(token, room, at(20, 10), at(20, 11)).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            long created = 0;
            long conflicts = 0;
            List<Integer> others = new ArrayList<>();
            for (Future<Integer> f : results) {
                int status = f.get(60, TimeUnit.SECONDS);
                if (status == 201) {
                    created++;
                } else if (status == 409) {
                    conflicts++;
                } else {
                    others.add(status);
                }
            }
            System.out.println("CONCURRENCY RESULT: 201=" + created + " 409=" + conflicts + " other=" + others);

            assertThat(others).isEmpty();
            assertThat(created).isEqualTo(1);
            assertThat(conflicts).isEqualTo(THREADS - 1);
        } finally {
            pool.shutdownNow();
        }
    }
}

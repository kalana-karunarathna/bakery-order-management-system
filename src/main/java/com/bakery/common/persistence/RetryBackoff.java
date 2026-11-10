package com.bakery.common.persistence;

import java.util.concurrent.ThreadLocalRandom;

/** Short, randomised pause between optimistic-locking retries so competing writers spread out. */
public final class RetryBackoff {

    /** Attempts for a contended read-modify-write before giving up with a "try again" message. */
    public static final int MAX_ATTEMPTS = 10;

    private RetryBackoff() {
    }

    /** Sleeps 5-20 ms times the attempt number (with jitter). */
    public static void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, 20) * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

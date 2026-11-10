package com.bakery.order.service;

/** File storage failed. The message is safe to show; the cause is only logged. */
public class OrderStorageException extends RuntimeException {

    public OrderStorageException(Throwable cause) {
        super("We couldn't reach the order records just now. Please try again.", cause);
    }
}

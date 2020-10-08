package com.injection;

public class CyclicInjection extends RuntimeException {
    public CyclicInjection() {
        super();
    }

    public CyclicInjection(String message) {
        super(message);
    }

    public CyclicInjection(String message, Throwable cause) {
        super(message, cause);
    }

    public CyclicInjection(Throwable cause) {
        super(cause);
    }
}

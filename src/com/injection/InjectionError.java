package com.injection;

public class InjectionError extends RuntimeException {
    public InjectionError() {
        super();
    }

    public InjectionError(String message) {
        super(message);
    }

    public InjectionError(String message, Throwable cause) {
        super(message, cause);
    }

    public InjectionError(Throwable cause) {
        super(cause);
    }
}

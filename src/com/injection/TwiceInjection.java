package com.injection;

public class TwiceInjection extends RuntimeException {
    public TwiceInjection() {
        super();
    }

    public TwiceInjection(String message) {
        super(message);
    }

    public TwiceInjection(String message, Throwable cause) {
        super(message, cause);
    }

    public TwiceInjection(Throwable cause) {
        super(cause);
    }
}

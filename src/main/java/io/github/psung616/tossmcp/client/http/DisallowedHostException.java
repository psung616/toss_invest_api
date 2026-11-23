package io.github.psung616.tossmcp.client.http;

public class DisallowedHostException extends RuntimeException {

    public DisallowedHostException(String message) {
        super(message);
    }
}

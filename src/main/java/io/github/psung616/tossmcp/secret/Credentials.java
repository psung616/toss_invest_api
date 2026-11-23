package io.github.psung616.tossmcp.secret;

public record Credentials(String clientId, String clientSecret) {

    @Override
    public String toString() {
        return "Credentials[****]";
    }
}

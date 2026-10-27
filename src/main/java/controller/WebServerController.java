package controller;

import java.io.IOException;
import java.net.InetSocketAddress;

public interface WebServerController extends AutoCloseable {
    int PORT = 3002;
    String SESSION_COOKIE = "TopSecretSession";

    void start() throws IOException;

    InetSocketAddress address();

    @Override
    void close();
}

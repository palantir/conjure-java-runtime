/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.ssl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocketFactory;
import jersey.repackaged.com.google.common.base.Throwables;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

/**
 * Tests for {@link SslSocketFactories} that test that SSL connections
 * can be established properly.
 */
public final class SslSocketFactoriesConnectionTests {

    private static final String TEST_HOST = "localhost";
    private static final int TEST_PORT = 8099;

    @Rule
    public Timeout testTimeout = Timeout.seconds(5);

    @Test
    public void testSslNoClientAuthenticationJks() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, false);
    }

    @Test
    public void testSslNoClientAuthenticationPkcs12() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, false);
    }

    @Test
    public void testSslNoClientAuthenticationFailsWithoutProperClientTrustStore() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        // bad configuration: client trust store does not contain any certificates
        // that can verify the server certificate
        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CLIENT_KEY_STORE_JKS_PATH)
                .build();

        try {
            runSslConnectionTest(serverConfig, clientConfig, false);
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(SSLHandshakeException.class)));
            assertThat(ex.getMessage(), containsString("PKIX path building failed"));
        }
    }

    @Test
    public void testSslWithClientAuthenticationJks() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CLIENT_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, true);
    }

    @Test
    public void testSslWithClientAuthenticationPkcs12() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CLIENT_KEY_STORE_P12_PATH)
                .keyStorePassword(TestConstants.CLIENT_KEY_STORE_P12_PASSWORD)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, true);
    }

    @Test
    public void testSslWithClientAuthenticationMixed() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.CLIENT_KEY_STORE_P12_PATH)
                .keyStorePassword(TestConstants.CLIENT_KEY_STORE_P12_PASSWORD)
                .build();

        runSslConnectionTest(serverConfig, clientConfig, true);
    }

    @Test
    public void testSslWithClientAuthenticationFailsWithoutClientKeyStore() {
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        // bad configuration: client does not specify a key store for a connection
        // that requires client authentication
        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CA_TRUST_STORE_PATH)
                .build();

        try {
            runSslConnectionTest(serverConfig, clientConfig, true);
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(SSLHandshakeException.class)));
            assertThat(ex.getMessage(), containsString("bad_certificate"));
        }
    }

    @Test
    public void testSslWithClientAuthenticationFailsWithoutProperServerTrustStore() {
        // bad configuration: server trust store does not contain any certificates
        // that can verify the client certificate
        SslConfiguration serverConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePath(TestConstants.SERVER_KEY_STORE_JKS_PATH)
                .keyStorePassword(TestConstants.SERVER_KEY_STORE_JKS_PASSWORD)
                .build();

        SslConfiguration clientConfig = SslConfiguration.builder()
                .trustStorePath(TestConstants.CLIENT_KEY_STORE_JKS_PATH)
                .build();

        try {
            runSslConnectionTest(serverConfig, clientConfig, false);
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(SSLHandshakeException.class)));
            assertThat(ex.getMessage(), containsString("PKIX path building failed"));
        }
    }

    private void runSslConnectionTest(
            SslConfiguration serverConfig,
            SslConfiguration clientConfig,
            boolean requireClientAuth) {
        String message = UUID.randomUUID().toString();

        SSLContext sslContext = SslSocketFactories.createSslContext(serverConfig);
        Thread serverThread = createSslServerThread(sslContext, requireClientAuth, message);
        serverThread.start();

        SSLSocketFactory sslSocketFactory = SslSocketFactories.createSslSocketFactory(clientConfig);
        verifySslConnection(sslSocketFactory, message);
    }

    /**
     * Verify that an SSL connection can be established. Creates an SSL socket with
     * the provided {@link SSLSocketFactory} that connect to {@link #TEST_HOST}
     * on {@link #TEST_PORT} and waits for expectedMessage to be sent by the server.
     */
    private void verifySslConnection(SSLSocketFactory factory, String expectedMessage) {
        try (Socket clientSocket = factory.createSocket(TEST_HOST, TEST_PORT)) {
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

            String fromServer;
            while ((fromServer = in.readLine()) != null) {
                assertThat(fromServer, is(expectedMessage));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return a {@link Thread} that opens an SSL server socket using the
     * provided context on {@link #TEST_PORT}. If a connection is established
     * on the socket, the specified message is sent to the client and the socket
     * is closed.
     */
    private Thread createSslServerThread(SSLContext sslContext, final boolean requireClientAuth, final String message) {
        final SSLServerSocketFactory factory = sslContext.getServerSocketFactory();
        Runnable serverThread = new Runnable() {
            @Override
            public void run() {
                try (SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(TEST_PORT)) {
                    sslServerSocket.setNeedClientAuth(requireClientAuth);
                    sslServerSocket.setReuseAddress(true);
                    Socket clientSocket = sslServerSocket.accept();

                    OutputStreamWriter streamWriter = new OutputStreamWriter(clientSocket.getOutputStream(),
                            StandardCharsets.UTF_8);

                    PrintWriter out = new PrintWriter(streamWriter, true);
                    out.println(message);
                    clientSocket.close();
                } catch (IOException e) {
                    Throwables.propagate(e);
                }

            }
        };

        return new Thread(serverThread);
    }

}

/*
 * Copyright 2015 Palantir Technologies, Inc. All rights reserved.
 */

package com.palantir.ssl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.palantir.remoting.ssl.KeyStoreConfiguration;
import com.palantir.remoting.ssl.SslConfiguration;
import com.palantir.remoting.ssl.SslSocketFactories;
import com.palantir.remoting.ssl.TrustStoreConfiguration;
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

    private enum ClientAuth {
        WITH_CLIENT_AUTH,
        NO_CLIENT_AUTH,
    }

    @Rule
    public Timeout testTimeout = Timeout.seconds(5);

    @Test
    public void testSslNoClientAuthenticationJks() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationPkcs12() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.builder()
                .path(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .type(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .password(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build();
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationFailsWithoutProperClientTrustStore() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        // bad configuration: client trust store does not contain any certificates
        // that can verify the server certificate
        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(
                TestConstants.CLIENT_KEY_STORE_JKS_PATH);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig);

        try {
            runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(SSLHandshakeException.class)));
            assertThat(ex.getMessage(), containsString("PKIX path building failed"));
        }
    }

    @Test
    public void testSslWithNoClientAuthenticationWorksWithoutProperServerTrustStore() {
        // if no client authentication is present, doesn't matter that server trust store
        // cannot verify any certificates
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslNoClientAuthenticationFailsWithMultipleKeyStoreSpecified() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);

        // specify that server key alias should be used
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.builder()
                .path(TestConstants.MULTIPLE_KEY_STORE_JKS_PATH)
                .password(TestConstants.MULTIPLE_KEY_STORE_JKS_PASSWORD)
                .alias(TestConstants.MULTIPLE_KEY_STORE_SERVER_ALIAS)
                .build();

        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);
        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.NO_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationJks() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration clientKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.CLIENT_KEY_STORE_JKS_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig, clientKeyStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationPkcs12() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);

        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.builder()
                .path(TestConstants.SERVER_KEY_STORE_P12_PATH)
                .type(TestConstants.SERVER_KEY_STORE_P12_TYPE)
                .password(TestConstants.SERVER_KEY_STORE_P12_PASSWORD)
                .build();

        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration clientKeyStoreConfig = KeyStoreConfiguration.builder()
                .path(TestConstants.CLIENT_KEY_STORE_P12_PATH)
                .type(TestConstants.CLIENT_KEY_STORE_P12_TYPE)
                .password(TestConstants.CLIENT_KEY_STORE_P12_PASSWORD)
                .build();
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig, clientKeyStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationMixed() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration clientKeyStoreConfig = KeyStoreConfiguration.builder()
                .path(TestConstants.CLIENT_KEY_STORE_P12_PATH)
                .type(TestConstants.CLIENT_KEY_STORE_P12_TYPE)
                .password(TestConstants.CLIENT_KEY_STORE_P12_PASSWORD)
                .build();
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig, clientKeyStoreConfig);

        runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
    }

    @Test
    public void testSslWithClientAuthenticationFailsWithoutClientKeyStore() {
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        // bad configuration: client does not specify a key store for a connection
        // that requires client authentication
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig);

        try {
            runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
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
        TrustStoreConfiguration serverTrustStoreConfig = TrustStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH);
        KeyStoreConfiguration serverKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.SERVER_KEY_STORE_JKS_PATH,
                TestConstants.SERVER_KEY_STORE_JKS_PASSWORD);
        SslConfiguration serverConfig = SslConfiguration.of(serverTrustStoreConfig, serverKeyStoreConfig);

        TrustStoreConfiguration clientTrustStoreConfig = TrustStoreConfiguration.of(TestConstants.CA_TRUST_STORE_PATH);
        KeyStoreConfiguration clientKeyStoreConfig = KeyStoreConfiguration.of(
                TestConstants.CLIENT_KEY_STORE_JKS_PATH,
                TestConstants.CLIENT_KEY_STORE_JKS_PASSWORD);
        SslConfiguration clientConfig = SslConfiguration.of(clientTrustStoreConfig, clientKeyStoreConfig);

        try {
            runSslConnectionTest(serverConfig, clientConfig, ClientAuth.WITH_CLIENT_AUTH);
            fail();
        } catch (RuntimeException ex) {
            assertThat(ex.getCause(), is(instanceOf(SSLHandshakeException.class)));
            assertThat(ex.getMessage(), containsString("bad_certificate"));
        }
    }

    private void runSslConnectionTest(
            SslConfiguration serverConfig,
            SslConfiguration clientConfig,
            ClientAuth clientAuth) {
        String message = UUID.randomUUID().toString();

        SSLContext sslContext = SslSocketFactories.createSslContext(serverConfig);
        SSLServerSocketFactory factory = sslContext.getServerSocketFactory();

        try (SSLServerSocket sslServerSocket = (SSLServerSocket) factory.createServerSocket(0)) {
            Thread serverThread = createSslServerThread(sslServerSocket, clientAuth, message);
            serverThread.start();

            SSLSocketFactory sslSocketFactory = SslSocketFactories.createSslSocketFactory(clientConfig);
            verifySslConnection(sslSocketFactory, sslServerSocket.getLocalPort(), message);
        } catch (IOException ex) {
            Throwables.propagate(ex);
        }
    }

    /**
     * Verify that an SSL connection can be established. Creates an SSL socket with
     * the provided {@link SSLSocketFactory} that connect to localhost on the specified
     * port and waits for expectedMessage to be sent by the server.
     */
    private void verifySslConnection(SSLSocketFactory factory, int port, String expectedMessage) {
        try (Socket clientSocket = factory.createSocket("localhost", port)) {
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
     * Return a {@link Thread} that accepts an incoming connection on the provided
     * {@link SSLServerSocket}. If a connection is established, the specified message
     * is sent to the client and the socket is closed.
     */
    private Thread createSslServerThread(
            final SSLServerSocket sslServerSocket,
            final ClientAuth clientAuth,
            final String message) {
        Runnable serverThread = new Runnable() {
            @Override
            public void run() {
                try {
                    sslServerSocket.setNeedClientAuth(clientAuth == ClientAuth.WITH_CLIENT_AUTH);
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

/*
 * Copyright 2016 Palantir Technologies, Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.remoting.ssl;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class KeyStoresTests {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void testCreateTrustStoreFromCertificateFile() throws GeneralSecurityException, IOException {
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(TestConstants.CA_DER_CERT_PATH);

        assertThat(trustStore.size(), is(1));
        assertThat(trustStore.getCertificate(TestConstants.CA_DER_CERT_PATH.getFileName().toString()).toString(),
                containsString("CN=testCA"));
    }

    @Test
    public void testCreateTrustStoreFromCertificateDirectory() throws GeneralSecurityException, IOException {
        File certFolder = tempFolder.newFolder();
        Files.copy(TestConstants.CA_DER_CERT_PATH.toFile(), certFolder.toPath().resolve("ca.der").toFile());
        Files.copy(TestConstants.SERVER_CERT_PEM_PATH.toFile(), certFolder.toPath().resolve("server.crt").toFile());
        Files.copy(TestConstants.CLIENT_CERT_PEM_PATH.toFile(), certFolder.toPath().resolve("client.cer").toFile());
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(certFolder.toPath());

        assertThat(trustStore.size(), is(3));
        assertThat(trustStore.getCertificate("ca.der").toString(), containsString("CN=testCA"));
        assertThat(trustStore.getCertificate("server.crt").toString(), containsString("CN=localhost"));
        assertThat(trustStore.getCertificate("client.cer").toString(), containsString("CN=localhost"));
    }

    @Test
    public void testCreateTrustStoreFromEmptyDirectory() throws GeneralSecurityException, IOException {
        File certFolder = tempFolder.newFolder();
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(certFolder.toPath());

        assertThat(trustStore.size(), is(0));
    }

    @Test
    public void testCreateTrustStoreFromDirectoryIgnoresHiddenFiles() throws IOException, GeneralSecurityException {
        File certFolder = tempFolder.newFolder();
        Files.copy(TestConstants.CA_DER_CERT_PATH.toFile(), certFolder.toPath().resolve(".hidden_file").toFile());
        KeyStore trustStore = KeyStores.createTrustStoreFromCertificates(certFolder.toPath());

        assertThat(trustStore.size(), is(0));
    }

    @Test
    public void testCreateTrustStoreFromDirectoryFailsWithNonCertFiles() throws IOException, GeneralSecurityException {
        File certFolder = tempFolder.newFolder();
        File tempCertFile = certFolder.toPath().resolve("crl.pem").toFile();
        Files.copy(TestConstants.CA_CRL_PATH.toFile(), tempCertFile);

        try {
            KeyStores.createTrustStoreFromCertificates(certFolder.toPath());
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getCause(), is(instanceOf(CertificateParsingException.class)));
            assertThat(e.getMessage(), containsString(
                    String.format("Could not read file at \"%s\" as an X.509 certificate",
                            tempCertFile.getAbsolutePath().toString())));
        }
    }

    @Test
    public void testCreateTrustStoreFromDirectoryFailsWithDirectories() throws IOException, GeneralSecurityException {
        File certFolder = tempFolder.newFolder();
        File tempDirFile = certFolder.toPath().resolve("childDir").toFile();
        boolean childDir = tempDirFile.mkdir();
        assertTrue(childDir);

        try {
            KeyStores.createTrustStoreFromCertificates(certFolder.toPath());
            fail();
        } catch (RuntimeException e) {
            assertThat(e.getCause(), is(instanceOf(CertificateException.class)));
            assertThat(e.getMessage(), containsString(
                    String.format("Could not read file at \"%s\" as an X.509 certificate",
                            tempDirFile.getAbsolutePath().toString())));
        }
    }

}

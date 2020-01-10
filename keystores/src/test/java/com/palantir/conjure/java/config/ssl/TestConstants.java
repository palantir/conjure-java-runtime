/*
 * (c) Copyright 2017 Palantir Technologies Inc. All rights reserved.
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

package com.palantir.conjure.java.config.ssl;

import com.google.common.io.BaseEncoding;
import com.palantir.conjure.java.api.config.ssl.SslConfiguration;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Test constants for trust stores and key stores used in unit tests.
 */
final class TestConstants {

    static final Path CA_TRUST_STORE_PATH = Paths.get("src", "test", "resources", "testCA", "testCA.jks");
    static final Path CA_DER_CERT_PATH = Paths.get("src", "test", "resources", "testCA", "testCA.der");
    static final Path CA_PEM_CERT_PATH = Paths.get("src", "test", "resources", "testCA", "testCA.cer");
    static final SslConfiguration.StoreType CA_TRUST_STORE_TYPE = SslConfiguration.StoreType.JKS;
    static final String CA_TRUST_STORE_JKS_PASSWORD = "caStore";

    static final Path SERVER_KEY_STORE_JKS_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServer.jks");
    static final SslConfiguration.StoreType SERVER_KEY_STORE_JKS_TYPE = SslConfiguration.StoreType.JKS;
    static final String SERVER_KEY_STORE_JKS_PASSWORD = "serverStore";

    static final Path SERVER_KEY_STORE_P12_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServer.p12");
    static final SslConfiguration.StoreType SERVER_KEY_STORE_P12_TYPE = SslConfiguration.StoreType.PKCS12;
    static final String SERVER_KEY_STORE_P12_PASSWORD = "testServer";

    static final Path SERVER_KEY_PEM_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServer.key");
    static final Path SERVER_CERT_PEM_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServer.cer");
    static final Path SERVER_KEY_CERT_COMBINED_PEM_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testServer",
            "testServer.pem");

    static final Path CLIENT_KEY_STORE_JKS_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testClient",
            "testClient.jks");
    static final String CLIENT_KEY_STORE_JKS_PASSWORD = "clientStore";

    static final Path CLIENT_KEY_STORE_P12_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testClient",
            "testClient.p12");
    static final SslConfiguration.StoreType CLIENT_KEY_STORE_P12_TYPE = SslConfiguration.StoreType.PKCS12;
    static final String CLIENT_KEY_STORE_P12_PASSWORD = "testClient";

    static final Path CLIENT_CERT_PEM_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testClient",
            "testClient.cer");
    static final Path CLIENT_KEY_CERT_COMBINED_PEM_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testClient",
            "testClient.pem");

    static final Path MULTIPLE_KEY_STORE_JKS_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "multiple.jks");
    static final String MULTIPLE_KEY_STORE_JKS_PASSWORD = "multiple";
    static final String MULTIPLE_KEY_STORE_CLIENT_ALIAS = "testClient";
    static final String MULTIPLE_KEY_STORE_SERVER_ALIAS = "testServer";

    static final Path CHILD_KEY_CERT_CHAIN_PEM_PATH = Paths.get(
            "src",
            "test",
            "resources",
            "testChild",
            "testChild_key_cert_chain.pem");
    static final Path COMBINED_CRL_PATH = Paths.get("src", "test", "resources", "crl", "combined.crl");

    private static final String RSA_KEY_STRING = "MIIEowIBAAKCAQEAttQaJnJC4vvwzgUXm3nrJRRAtExasHaj92IoHdpe1+BOtj9vuT6jf"
            + "pYny+0Bgo6GDEEEdDBIS9YdoMKDA9PKz/5WmptF5Rc0r5SBzzyVRZUkrU8C4Vc8zgkm/ICWRU/m7AqcwLYAB9q8a3kh0nV6wRSDYpQSo"
            + "YCcwhHll2mJDNnXYcVusWvYeEF8RSAb6xIqmcDQQ+buHkbbsnsfz9A5S9RKt2fsHXJky4yC00cWeVGuOaX2ToTwvYQCFoWNGoZQmCtXK"
            + "Y3fpr57RoS+Pm9AQ/dKa/zm1lAcSHhh1SEwizMZBrb3glghm7OoV1LcEZiEkeby5CJaAhUe2tzOA8aMywIDAQABAoIBAQClW0yTfUB1n"
            + "zx6eSyS2EAO9zRWXcCgXY2LD2INLhYd1agbOWDJAJhKw3AXBrMY6+Ldbmii1ZBt1qhRX9uzOHj0iaq2hr0+qDBkpUKKttajanMTtKR8P"
            + "rSl/K0gLwS6h7vpvOdtfK6ma/WClro6aTqvKuhx3k7Dd1lQRXOL8E2eKlk2SXzEXoU52G+GFvZHN01q9F2u3NTsw1L7Y6klikuEifvYQ"
            + "Pqkp9Y8vBA4gsXFjOsfrqoQVmDSEOvRYvpFo14Uz4UrFPBJXxqk9ooBgefQBSS4r8nJo2MOlgAYVuRqmBUPiulfnUv3dItE4THCcZO2A"
            + "mXRJOnXB9l9DReZXurxAoGBANwkc1iJU148lq6zJ6zPiNeybEhK/nPX8XGFuJxQ2RazcizxfsPpnomxUIqpzNpWN1S6u3qJIBl3Y7U5i"
            + "Jf+2tkuflwFQe7Dt7RiolTiRNpIN8MjwjjhjPjtTDtXrXCBtjy5yCRQzYXFCrOEe/ttAYQDsagnNy+3GPjyZY2pgZO/AoGBANSburZYo"
            + "ErK27sDie/hQADGGfAWcE8LRozTqo7/E2TEJ0YKrollqnfTP0GbcOVrmyeBhFDqE7NY4UvXtzDlibYAfFUuHg82hcd393Uzyu1Y9IBNs"
            + "dzPbtsIEl8/dnEftfVPCDLzUfI9XKEQmRaUSNH/0A6cosRiP37DAnTISZn1AoGAJSlGC2ELILJLoWv+u45BBGBLJRz7vSRrzoULN9/x9"
            + "YIPOQT/KCUsrQOwm+ez+/tn1ba75SB2ubXMsA/pPfc4jEbr7663hY2mWh34VynnA44DU76aj62LdY3hO3c+gOp0j+Wwomi9eOJdPxaTM"
            + "0sgYV+aQs9z4msfHQ8WE4bUp9MCgYA6Sjf2pnXMC7ISo/W9ftQ9YhLacEx6X20IT0AD1ItMpTrfSS5xHR6Pm6tMIYHiZI41Vp0gUgz0r"
            + "vmWAZ6IGWaYL6nm8K3tHdWvyoRd7cVFLY5bXvHUyEpsYtomow+mFDue9fwZe/yLnac3wYU3W5BbgvdYCjnV/dnKt0yFGCSVwQKBgBSrX"
            + "PrymLYQ2+gD8rm8sx05BQ7WWq4qLZFWQSBcmhVv9hEcHblTDcki95dwir6Pmo6xsErSgbXKQMYUdOVoutVG1aV3sWL1eBrR93j8x3sQ+"
            + "TgGD5I/JOeUZhLCRYmYQoWQA9hl24cVcoY/85KHPN+PxKDtbGwOlnc9Dyw14XN2";
    static final byte[] PRIVATE_KEY_DER = BaseEncoding.base64().decode(RSA_KEY_STRING);

    static final String RSA_PRIVATE_KEY_TAGGED_STRING =
            "-----BEGIN RSA PRIVATE KEY-----\n" + RSA_KEY_STRING + "\n-----END RSA PRIVATE KEY-----";

    private static final String PKCS8_KEY_STRING = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC21BomckLi+/DOBRe"
            + "beeslFEC0TFqwdqP3Yigd2l7X4E62P2+5PqN+lifL7QGCjoYMQQR0MEhL1h2gwoMD08rP/laam0XlFzSvlIHPPJVFlSStTwLhVzzOCSb"
            + "8gJZFT+bsCpzAtgAH2rxreSHSdXrBFINilBKhgJzCEeWXaYkM2ddhxW6xa9h4QXxFIBvrEiqZwNBD5u4eRtuyex/P0DlL1Eq3Z+wdcmT"
            + "LjILTRxZ5Ua45pfZOhPC9hAIWhY0ahlCYK1cpjd+mvntGhL4+b0BD90pr/ObWUBxIeGHVITCLMxkGtveCWCGbs6hXUtwRmISR5vLkIlo"
            + "CFR7a3M4DxozLAgMBAAECggEBAKVbTJN9QHWfPHp5LJLYQA73NFZdwKBdjYsPYg0uFh3VqBs5YMkAmErDcBcGsxjr4t1uaKLVkG3WqFF"
            + "f27M4ePSJqraGvT6oMGSlQoq21qNqcxO0pHw+tKX8rSAvBLqHu+m85218rqZr9YKWujppOq8q6HHeTsN3WVBFc4vwTZ4qWTZJfMRehTn"
            + "Yb4YW9kc3TWr0Xa7c1OzDUvtjqSWKS4SJ+9hA+qSn1jy8EDiCxcWM6x+uqhBWYNIQ69Fi+kWjXhTPhSsU8ElfGqT2igGB59AFJLivycm"
            + "jYw6WABhW5GqYFQ+K6V+dS/d0i0ThMcJxk7YCZdEk6dcH2X0NF5le6vECgYEA3CRzWIlTXjyWrrMnrM+I17JsSEr+c9fxcYW4nFDZFrN"
            + "yLPF+w+meibFQiqnM2lY3VLq7eokgGXdjtTmIl/7a2S5+XAVB7sO3tGKiVOJE2kg3wyPCOOGM+O1MO1etcIG2PLnIJFDNhcUKs4R7+20"
            + "BhAOxqCc3L7cY+PJljamBk78CgYEA1Ju6tligSsrbuwOJ7+FAAMYZ8BZwTwtGjNOqjv8TZMQnRgquiWWqd9M/QZtw5WubJ4GEUOoTs1j"
            + "hS9e3MOWJtgB8VS4eDzaFx3f3dTPK7Vj0gE2x3M9u2wgSXz92cR+19U8IMvNR8j1coRCZFpRI0f/QDpyixGI/fsMCdMhJmfUCgYAlKUY"
            + "LYQsgskuha/67jkEEYEslHPu9JGvOhQs33/H1gg85BP8oJSytA7Cb57P7+2fVtrvlIHa5tcywD+k99ziMRuvvrreFjaZaHfhXKecDjgN"
            + "TvpqPrYt1jeE7dz6A6nSP5bCiaL144l0/FpMzSyBhX5pCz3Piax8dDxYThtSn0wKBgDpKN/amdcwLshKj9b1+1D1iEtpwTHpfbQhPQAP"
            + "Ui0ylOt9JLnEdHo+bq0whgeJkjjVWnSBSDPSu+ZYBnogZZpgvqebwre0d1a/KhF3txUUtjlte8dTISmxi2iajD6YUO571/Bl7/Iudpzf"
            + "BhTdbkFuC91gKOdX92cq3TIUYJJXBAoGAFKtc+vKYthDb6APyubyzHTkFDtZariotkVZBIFyaFW/2ERwduVMNySL3l3CKvo+ajrGwStK"
            + "BtcpAxhR05Wi61UbVpXexYvV4GtH3ePzHexD5OAYPkj8k55RmEsJFiZhChZAD2GXbhxVyhj/zkoc834/EoO1sbA6Wdz0PLDXhc3Y=";
    static final byte[] PKCS8_PRIVATE_KEY_DER = BaseEncoding.base64().decode(PKCS8_KEY_STRING);

    static final String PKCS8_PRIVATE_KEY_TAGGED_STRING =
            "-----BEGIN PRIVATE KEY-----\n" + PKCS8_KEY_STRING + "\n-----END PRIVATE KEY-----";

    static final BigInteger MODULUS = new BigInteger("00b6d41a267242e2fbf0ce05179b79"
            + "eb251440b44c5ab076a3f762281dda5ed7e04eb63f6fb93ea37e9627cbed01828e860c41047430484bd61da0c28303d3cacffe56"
            + "9a9b45e51734af9481cf3c95459524ad4f02e1573cce0926fc8096454fe6ec0a9cc0b60007dabc6b7921d2757ac11483629412a1"
            + "809cc211e59769890cd9d761c56eb16bd878417c45201beb122a99c0d043e6ee1e46dbb27b1fcfd0394bd44ab767ec1d7264cb8c"
            + "82d347167951ae39a5f64e84f0bd840216858d1a8650982b57298ddfa6be7b4684be3e6f4043f74a6bfce6d6501c487861d52130"
            + "8b331906b6f78258219bb3a85752dc11988491e6f2e4225a02151edadcce03c68ccb", 16);

    static final BigInteger PRIVATE_EXPONENT = new BigInteger("00a55b4c937d40759f3c7a792c92d8400ef734565dc0a05d8d8b0f62"
            + "0d2e161dd5a81b3960c900984ac3701706b318ebe2dd6e68a2d5906dd6a8515fdbb33878f489aab686bd3ea83064a5428ab6d6a3"
            + "6a7313b4a47c3eb4a5fcad202f04ba87bbe9bce76d7caea66bf58296ba3a693aaf2ae871de4ec377595045738bf04d9e2a593649"
            + "7cc45e8539d86f8616f647374d6af45daedcd4ecc352fb63a9258a4b8489fbd840faa4a7d63cbc103882c5c58ceb1faeaa105660"
            + "d210ebd162fa45a35e14cf852b14f0495f1aa4f68a0181e7d00524b8afc9c9a3630e96001856e46a98150f8ae95f9d4bf7748b44"
            + "e131c27193b60265d124e9d707d97d0d17995eeaf1", 16);

    static final BigInteger PUBLIC_EXPONENT = new BigInteger("65537", 10);

    static final BigInteger PRIME_P = new BigInteger("00dc24735889535e3c96aeb327accf88d7b26c484afe73d7f17185b89c50d916b"
            + "3722cf17ec3e99e89b1508aa9ccda563754babb7a8920197763b5398897fedad92e7e5c0541eec3b7b462a254e244da4837c323c"
            + "238e18cf8ed4c3b57ad7081b63cb9c82450cd85c50ab3847bfb6d018403b1a827372fb718f8f2658da98193bf", 16);

    static final BigInteger PRIME_Q = new BigInteger("00d49bbab658a04acadbbb0389efe14000c619f016704f0b468cd3aa8eff1364c"
            + "427460aae8965aa77d33f419b70e56b9b27818450ea13b358e14bd7b730e589b6007c552e1e0f3685c777f77533caed58f4804db"
            + "1dccf6edb08125f3f76711fb5f54f0832f351f23d5ca11099169448d1ffd00e9ca2c4623f7ec30274c84999f5", 16);

    static final BigInteger EXPONENT_P = new BigInteger("2529460b610b20b24ba16bfebb8e4104604b251cfbbd246bce850b37dff1f5"
            + "820f3904ff28252cad03b09be7b3fbfb67d5b6bbe52076b9b5ccb00fe93df7388c46ebefaeb7858da65a1df85729e7038e0353be"
            + "9a8fad8b758de13b773e80ea748fe5b0a268bd78e25d3f1693334b20615f9a42cf73e26b1f1d0f161386d4a7d3", 16);

    static final BigInteger EXPONENT_Q = new BigInteger("3a4a37f6a675cc0bb212a3f5bd7ed43d6212da704c7a5f6d084f4003d48b4c"
            + "a53adf492e711d1e8f9bab4c2181e2648e35569d20520cf4aef996019e881966982fa9e6f0aded1dd5afca845dedc5452d8e5b5e"
            + "f1d4c84a6c62da26a30fa6143b9ef5fc197bfc8b9da737c185375b905b82f7580a39d5fdd9cab74c85182495c1", 16);

    static final BigInteger CTR_COEFFICIENT = new BigInteger("14ab5cfaf298b610dbe803f2b9bcb31d39050ed65aae2a2d915641205"
            + "c9a156ff6111c1db9530dc922f797708abe8f9a8eb1b04ad281b5ca40c61474e568bad546d5a577b162f5781ad1f778fcc77b10f"
            + "938060f923f24e7946612c245899842859003d865db871572863ff392873cdf8fc4a0ed6c6c0e96773d0f2c35e17376", 16);

    private TestConstants() {}

}

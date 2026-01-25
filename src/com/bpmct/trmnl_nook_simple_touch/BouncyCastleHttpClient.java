package com.bpmct.trmnl_nook_simple_touch;

import android.content.Context;
import android.util.Log;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.x500.X500Principal;

import org.spongycastle.tls.AlertDescription;
import org.spongycastle.tls.CertificateRequest;
import org.spongycastle.tls.CipherSuite;
import org.spongycastle.tls.DefaultTlsClient;
import org.spongycastle.tls.NameType;
import org.spongycastle.tls.ProtocolVersion;
import org.spongycastle.tls.ServerName;
import org.spongycastle.tls.ServerNameList;
import org.spongycastle.tls.TlsAuthentication;
import org.spongycastle.tls.TlsClientProtocol;
import org.spongycastle.tls.TlsCredentials;
import org.spongycastle.tls.TlsFatalAlert;
import org.spongycastle.tls.TlsServerCertificate;
import org.spongycastle.tls.TlsExtensionsUtils;
import org.spongycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.spongycastle.tls.crypto.TlsCertificate;

/**
 * HTTP client using BouncyCastle/SpongyCastle TLS for TLS 1.2 support on Android 2.1.
 *
 * This bypasses the system's old OpenSSL and uses BouncyCastle's pure-Java TLS implementation.
 *
 * Requires SpongyCastle JARs in libs/:
 * - spongycastle-core-1.58.0.0.jar
 * - spongycastle-prov-1.58.0.0.jar
 * - spongycastle-bctls-jdk15on-1.58.0.0.jar
 *
 * Requires a CA bundle at res/raw/ca_bundle.pem
 */
public class BouncyCastleHttpClient {
    private static final String TAG = "BCHttpClient";
    private static final boolean bcAvailable = true;
    private static X509TrustManager trustManager = null;

    public static boolean isAvailable() {
        return bcAvailable;
    }
    
    /**
     * Makes an HTTPS GET request using BouncyCastle TLS.
     * Returns response body as string, or error message on failure.
     * batteryLevel 0–100 is sent as battery-level header when >= 0; use -1 to omit.
     */
    public static String getHttps(Context context, String url, String apiId, String apiToken, int batteryLevel) {
        try {
            return getHttpsImpl(context, url, apiId, apiToken, batteryLevel);
        } catch (Throwable t) {
            Log.e(TAG, "BouncyCastle HTTPS failed", t);
            // Get full error message including class name
            String errorMsg = t.getClass().getSimpleName();
            if (t.getMessage() != null) {
                errorMsg += ": " + t.getMessage();
            }
            // Log full stack trace
            java.io.StringWriter sw = new java.io.StringWriter();
            java.io.PrintWriter pw = new java.io.PrintWriter(sw);
            t.printStackTrace(pw);
            Log.e(TAG, "BC full stack trace:\n" + sw.toString());
            return "Error: " + errorMsg;
        }
    }

    public static String getHttps(Context context, String url, String apiId, String apiToken) {
        return getHttps(context, url, apiId, apiToken, -1);
    }

    public static String getHttps(String url, String apiId, String apiToken) {
        return getHttps(null, url, apiId, apiToken, -1);
    }

    /**
     * Makes an HTTPS GET request and returns raw bytes (e.g., images).
     * Returns null on failure.
     */
    public static byte[] getHttpsBytes(Context context, String url) {
        try {
            return getHttpsBytesImpl(context, url);
        } catch (Throwable t) {
            Log.e(TAG, "BouncyCastle HTTPS bytes failed", t);
            return null;
        }
    }
    
    private static String getHttpsImpl(Context context, String url, String apiId, String apiToken, int batteryLevel) throws Exception {
        // Parse URL
        java.net.URL u = new java.net.URL(url);
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 443;
        String path = u.getPath();
        if (path == null || path.length() == 0) {
            path = "/";
        }
        if (u.getQuery() != null) {
            path += "?" + u.getQuery();
        }
        
        Log.d(TAG, "BC connecting to " + host + ":" + port + path);
        
        // Create socket
        Socket socket = new Socket(host, port);
        socket.setSoTimeout(20000);
        
        try {
            // Create TLS client protocol (SpongyCastle 1.58 uses 2-arg constructor)
            TlsClientProtocol tlsProtocol = new TlsClientProtocol(
                    socket.getInputStream(), socket.getOutputStream());
            Log.d(TAG, "BC created TlsClientProtocol instance");

            // Create TLS client (CA-validated)
            X509TrustManager tm = getTrustManager(context);
            if (tm == null) {
                return "Error: CA bundle not available (res/raw/ca_bundle.pem)";
            }
            DefaultTlsClient tlsClient = createTlsClient(host, tm);

            // Connect
            tlsProtocol.connect(tlsClient);
            
            Log.d(TAG, "BC TLS handshake successful");
            
            // Get streams
            OutputStream tlsOut = tlsProtocol.getOutputStream();
            InputStream tlsIn = tlsProtocol.getInputStream();
            
            // Send HTTP request
            PrintWriter writer = new PrintWriter(tlsOut, true);
            writer.print("GET " + path + " HTTP/1.1\r\n");
            writer.print("Host: " + host + "\r\n");
            writer.print("User-Agent: TRMNL-Nook/1.0 (Android 2.1)\r\n");
            writer.print("Accept: application/json\r\n");
            if (apiId != null) {
                writer.print("ID: " + apiId + "\r\n");
            }
            if (apiToken != null) {
                writer.print("access-token: " + apiToken + "\r\n");
            }
            if (batteryLevel >= 0) {
                writer.print("battery-level: " + batteryLevel + "\r\n");
                writer.print("Battery-Voltage: " + batteryLevel + "\r\n");
            }
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();
            
            // Read HTTP response
            String statusLine = readAsciiLine(tlsIn);
            if (statusLine == null) {
                return "Error: No response from server";
            }
            
            Log.d(TAG, "BC response: " + statusLine);
            
            // Parse status code
            int statusCode = 0;
            try {
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) {
                    statusCode = Integer.parseInt(parts[1]);
                }
            } catch (Exception e) {
                // Ignore
            }
            
            // Read headers until blank line
            String line;
            int contentLength = -1;
            boolean chunked = false;
            while ((line = readAsciiLine(tlsIn)) != null) {
                if (line.length() == 0) break;
                String lower = line.toLowerCase();
                if (lower.startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception e) {
                        // Ignore
                    }
                } else if (lower.startsWith("transfer-encoding:") && lower.indexOf("chunked") != -1) {
                    chunked = true;
                }
            }
            
            // Read body (bytes first, then decode)
            ByteArrayOutputStream bodyBytes = new ByteArrayOutputStream();
            if (chunked) {
                while (true) {
                    String sizeLine = readAsciiLine(tlsIn);
                    if (sizeLine == null) break;
                    int semicolon = sizeLine.indexOf(';');
                    if (semicolon > 0) {
                        sizeLine = sizeLine.substring(0, semicolon);
                    }
                    int size = 0;
                    try {
                        size = Integer.parseInt(sizeLine.trim(), 16);
                    } catch (Exception e) {
                        return "Error: Invalid chunk size";
                    }
                    if (size <= 0) {
                        // Consume trailing headers after last chunk
                        while ((line = readAsciiLine(tlsIn)) != null && line.length() > 0) {
                            // Ignore
                        }
                        break;
                    }
                    int remaining = size;
                    byte[] buf = new byte[8192];
                    while (remaining > 0) {
                        int n = tlsIn.read(buf, 0, Math.min(buf.length, remaining));
                        if (n <= 0) break;
                        bodyBytes.write(buf, 0, n);
                        remaining -= n;
                    }
                    // Consume CRLF after chunk
                    readAsciiLine(tlsIn);
                }
            } else if (contentLength > 0) {
                byte[] buf = new byte[8192];
                int total = 0;
                while (total < contentLength) {
                    int n = tlsIn.read(buf, 0, Math.min(buf.length, contentLength - total));
                    if (n <= 0) break;
                    bodyBytes.write(buf, 0, n);
                    total += n;
                }
            } else {
                byte[] buf = new byte[8192];
                int n;
                while ((n = tlsIn.read(buf)) > 0) {
                    bodyBytes.write(buf, 0, n);
                }
            }
            String body = new String(bodyBytes.toByteArray(), "UTF-8");
            
            if (statusCode >= 200 && statusCode < 300) {
                Log.d(TAG, "BC got " + body.length() + " chars");
                return body;
            } else {
                return "Error: HTTP " + statusCode + " " + body;
            }
            
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private static byte[] getHttpsBytesImpl(Context context, String url) throws Exception {
        java.net.URL u = new java.net.URL(url);
        String host = u.getHost();
        int port = u.getPort() > 0 ? u.getPort() : 443;
        String path = u.getPath();
        if (path == null || path.length() == 0) {
            path = "/";
        }
        if (u.getQuery() != null) {
            path += "?" + u.getQuery();
        }

        Log.d(TAG, "BC connecting (bytes) to " + host + ":" + port + path);

        Socket socket = new Socket(host, port);
        socket.setSoTimeout(20000);
        try {
            TlsClientProtocol tlsProtocol = new TlsClientProtocol(
                    socket.getInputStream(), socket.getOutputStream());
            Log.d(TAG, "BC created TlsClientProtocol instance (bytes)");

            X509TrustManager tm = getTrustManager(context);
            if (tm == null) {
                Log.e(TAG, "BC CA bundle not available for bytes request");
                return null;
            }
            DefaultTlsClient tlsClient = createTlsClient(host, tm);

            tlsProtocol.connect(tlsClient);
            Log.d(TAG, "BC TLS handshake successful (bytes)");

            OutputStream tlsOut = tlsProtocol.getOutputStream();
            InputStream tlsIn = tlsProtocol.getInputStream();

            PrintWriter writer = new PrintWriter(tlsOut, true);
            writer.print("GET " + path + " HTTP/1.1\r\n");
            writer.print("Host: " + host + "\r\n");
            writer.print("User-Agent: TRMNL-Nook/1.0 (Android 2.1)\r\n");
            writer.print("Accept: image/*\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();

            String statusLine = readAsciiLine(tlsIn);
            if (statusLine == null) {
                Log.e(TAG, "BC bytes: no response from server");
                return null;
            }

            int statusCode = 0;
            try {
                String[] parts = statusLine.split(" ");
                if (parts.length >= 2) {
                    statusCode = Integer.parseInt(parts[1]);
                }
            } catch (Exception e) {
                // Ignore
            }

            String line;
            int contentLength = -1;
            while ((line = readAsciiLine(tlsIn)) != null) {
                if (line.length() == 0) break;
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }

            if (statusCode < 200 || statusCode >= 300) {
                Log.e(TAG, "BC bytes: HTTP " + statusCode);
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (contentLength > 0) {
                byte[] buf = new byte[8192];
                int total = 0;
                while (total < contentLength) {
                    int n = tlsIn.read(buf, 0, Math.min(buf.length, contentLength - total));
                    if (n <= 0) break;
                    baos.write(buf, 0, n);
                    total += n;
                }
            } else {
                byte[] buf = new byte[8192];
                int n;
                while ((n = tlsIn.read(buf)) > 0) {
                    baos.write(buf, 0, n);
                }
            }

            return baos.toByteArray();
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    private static String readAsciiLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        int c;
        boolean gotCR = false;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                gotCR = true;
                continue;
            }
            if (c == '\n') {
                break;
            }
            if (gotCR) {
                line.write('\r');
                gotCR = false;
            }
            line.write(c);
        }
        if (c == -1 && line.size() == 0) {
            return null;
        }
        return new String(line.toByteArray(), "ISO-8859-1");
    }
    
    private static DefaultTlsClient createTlsClient(final String hostname, final X509TrustManager tm) {
        SecureRandom secureRandom = new SecureRandom();
        final BcTlsCrypto crypto = new BcTlsCrypto(secureRandom);

        return new DefaultTlsClient(crypto) {
            public ProtocolVersion getClientVersion() {
                return ProtocolVersion.TLSv12;
            }

            public ProtocolVersion getMinimumVersion() {
                return ProtocolVersion.TLSv10;
            }

            public int[] getCipherSuites() {
                return new int[] {
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA
                };
            }

            public Hashtable getClientExtensions() throws java.io.IOException {
                Hashtable extensions = super.getClientExtensions();
                extensions = TlsExtensionsUtils.ensureExtensionsInitialised(extensions);

                // Add SNI (Server Name Indication) for modern hosts
                if (hostname != null && hostname.length() > 0) {
                    Vector serverNames = new Vector();
                    serverNames.addElement(new ServerName(NameType.host_name, hostname));
                    TlsExtensionsUtils.addServerNameExtension(
                            extensions, new ServerNameList(serverNames));
                }

                // Advertise extended master secret support
                TlsExtensionsUtils.addExtendedMasterSecretExtension(extensions);

                return extensions;
            }

            public TlsAuthentication getAuthentication() {
                return new TlsAuthentication() {
                    public void notifyServerCertificate(TlsServerCertificate serverCertificate) throws java.io.IOException {
                        if (tm == null) {
                            throw new TlsFatalAlert(AlertDescription.bad_certificate);
                        }
                        try {
                            X509Certificate[] chain = toX509Chain(serverCertificate);
                            if (chain == null || chain.length == 0) {
                                throw new TlsFatalAlert(AlertDescription.bad_certificate);
                            }
                            String authType = chain[0].getPublicKey().getAlgorithm();
                            tm.checkServerTrusted(chain, authType);
                            if (!verifyHostname(hostname, chain[0])) {
                                Log.e(TAG, "BC hostname verification failed for " + hostname);
                                throw new TlsFatalAlert(AlertDescription.bad_certificate);
                            }
                            Log.d(TAG, "BC server certificate validated against CA bundle");
                        } catch (TlsFatalAlert e) {
                            throw e;
                        } catch (Throwable t) {
                            Log.e(TAG, "BC certificate validation failed", t);
                            throw new TlsFatalAlert(AlertDescription.bad_certificate);
                        }
                    }

                    public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
                        return null; // No client cert
                    }
                };
            }
        };
    }

    private static synchronized X509TrustManager getTrustManager(Context context) {
        if (trustManager != null) {
            return trustManager;
        }
        if (context == null) {
            Log.w(TAG, "BC no context; cannot load CA bundle");
            return null;
        }
        InputStream is = null;
        try {
            is = context.getResources().openRawResource(R.raw.ca_bundle);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            Collection certs = cf.generateCertificates(is);
            if (certs == null || certs.size() == 0) {
                Log.e(TAG, "BC CA bundle is empty");
                return null;
            }

            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            int i = 0;
            java.util.Iterator it = certs.iterator();
            while (it.hasNext()) {
                java.security.cert.Certificate cert = (java.security.cert.Certificate) it.next();
                ks.setCertificateEntry("ca-" + i, cert);
                i++;
            }

            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);
            javax.net.ssl.TrustManager[] tms = tmf.getTrustManagers();
            for (int j = 0; j < tms.length; j++) {
                if (tms[j] instanceof X509TrustManager) {
                    trustManager = (X509TrustManager) tms[j];
                    return trustManager;
                }
            }

            Log.e(TAG, "BC no X509TrustManager available");
            return null;
        } catch (Throwable t) {
            Log.e(TAG, "BC failed to load CA bundle", t);
            return null;
        } finally {
            if (is != null) {
                try { is.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static X509Certificate[] toX509Chain(TlsServerCertificate serverCertificate) throws Exception {
        if (serverCertificate == null || serverCertificate.getCertificate() == null) {
            return null;
        }
        org.spongycastle.tls.Certificate tlsCert = serverCertificate.getCertificate();
        TlsCertificate[] bcCerts = tlsCert.getCertificateList();
        if (bcCerts == null || bcCerts.length == 0) {
            return null;
        }
        X509Certificate[] chain = new X509Certificate[bcCerts.length];
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        for (int i = 0; i < bcCerts.length; i++) {
            byte[] encoded = bcCerts[i].getEncoded();
            ByteArrayInputStream bais = new ByteArrayInputStream(encoded);
            chain[i] = (X509Certificate) cf.generateCertificate(bais);
        }
        return chain;
    }

    private static boolean verifyHostname(String hostname, X509Certificate cert) {
        if (hostname == null || hostname.length() == 0 || cert == null) {
            return false;
        }
        String host = hostname.toLowerCase();
        try {
            Collection altNames = cert.getSubjectAlternativeNames();
            if (altNames != null) {
                java.util.Iterator it = altNames.iterator();
                while (it.hasNext()) {
                    Object entryObj = it.next();
                    if (!(entryObj instanceof List)) {
                        continue;
                    }
                    List entry = (List) entryObj;
                    if (entry.size() < 2) {
                        continue;
                    }
                    Integer type = (Integer) entry.get(0);
                    Object value = entry.get(1);
                    if (type != null && value != null) {
                        if (type.intValue() == 2) { // dNSName
                            String dns = value.toString().toLowerCase();
                            if (matchHostname(host, dns)) {
                                return true;
                            }
                        } else if (type.intValue() == 7) { // iPAddress
                            if (host.equals(value.toString().toLowerCase())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "BC SAN parse failed, falling back to CN: " + t);
        }

        String cn = getCommonName(cert);
        if (cn != null && matchHostname(host, cn.toLowerCase())) {
            return true;
        }
        return false;
    }

    private static String getCommonName(X509Certificate cert) {
        try {
            X500Principal principal = cert.getSubjectX500Principal();
            if (principal == null) {
                return null;
            }
            String dn = principal.getName("RFC2253");
            String[] parts = dn.split(",");
            for (int i = 0; i < parts.length; i++) {
                String part = parts[i].trim();
                if (part.startsWith("CN=") && part.length() > 3) {
                    return part.substring(3);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "BC CN parse failed: " + t);
        }
        return null;
    }

    private static boolean matchHostname(String hostname, String pattern) {
        if (hostname == null || pattern == null) {
            return false;
        }
        String host = hostname.toLowerCase();
        String pat = pattern.toLowerCase();
        if (host.equals(pat)) {
            return true;
        }
        if (pat.startsWith("*.")) {
            String suffix = pat.substring(1); // ".example.com"
            if (host.endsWith(suffix)) {
                String prefix = host.substring(0, host.length() - suffix.length());
                return prefix.length() > 0 && prefix.indexOf('.') == -1;
            }
        }
        return false;
    }
}

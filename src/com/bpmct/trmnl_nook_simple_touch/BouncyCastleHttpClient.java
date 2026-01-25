package com.bpmct.trmnl_nook_simple_touch;

import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Hashtable;
import java.util.Vector;

import org.spongycastle.tls.Certificate;
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
import org.spongycastle.tls.TlsServerCertificate;
import org.spongycastle.tls.TlsExtensionsUtils;
import org.spongycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * HTTP client using BouncyCastle/SpongyCastle TLS for TLS 1.2/1.3 support on Android 2.1.
 * 
 * This bypasses the system's old OpenSSL and uses BouncyCastle's pure-Java TLS implementation.
 * 
 * Requires SpongyCastle JARs in libs/:
 * - spongycastle-core-1.58.0.0.jar
 * - spongycastle-prov-1.58.0.0.jar  
 * - spongycastle-bctls-jdk15on-1.58.0.0.jar
 */
public class BouncyCastleHttpClient {
    private static final String TAG = "BCHttpClient";
    private static final boolean bcAvailable = true;

    public static boolean isAvailable() {
        return bcAvailable;
    }
    
    /**
     * Makes an HTTPS GET request using BouncyCastle TLS.
     * Returns response body as string, or error message on failure.
     */
    public static String getHttps(String url, String apiId, String apiToken) {
        try {
            return getHttpsImpl(url, apiId, apiToken);
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
    
    private static String getHttpsImpl(String url, String apiId, String apiToken) throws Exception {
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

            // Create TLS client (trust-all)
            DefaultTlsClient tlsClient = createTlsClient(host);

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
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();
            
            // Read HTTP response
            BufferedReader reader = new BufferedReader(new InputStreamReader(tlsIn, "UTF-8"));
            String statusLine = reader.readLine();
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
            while ((line = reader.readLine()) != null) {
                if (line.length() == 0) break;
                if (line.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
            
            // Read body
            StringBuilder body = new StringBuilder();
            if (statusCode >= 200 && statusCode < 300) {
                if (contentLength > 0) {
                    char[] buf = new char[contentLength];
                    int total = 0;
                    while (total < contentLength) {
                        int n = reader.read(buf, total, contentLength - total);
                        if (n <= 0) break;
                        total += n;
                    }
                    body.append(buf, 0, total);
                } else {
                    // Read until EOF
                    char[] buf = new char[8192];
                    int n;
                    while ((n = reader.read(buf)) > 0) {
                        body.append(buf, 0, n);
                    }
                }
            } else {
                // Read error body
                char[] buf = new char[8192];
                int n;
                while ((n = reader.read(buf)) > 0) {
                    body.append(buf, 0, n);
                }
            }
            
            if (statusCode >= 200 && statusCode < 300) {
                Log.d(TAG, "BC got " + body.length() + " chars");
                return body.toString();
            } else {
                return "Error: HTTP " + statusCode + " " + body.toString();
            }
            
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                // Ignore
            }
        }
    }
    
    private static DefaultTlsClient createTlsClient(final String hostname) {
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
                    public void notifyServerCertificate(TlsServerCertificate serverCertificate) {
                        // Trust all certificates (for testing only)
                        Log.d(TAG, "BC trusting server certificate (trust-all mode)");
                    }

                    public TlsCredentials getClientCredentials(CertificateRequest certificateRequest) {
                        return null; // No client cert
                    }
                };
            }
        };
    }
}

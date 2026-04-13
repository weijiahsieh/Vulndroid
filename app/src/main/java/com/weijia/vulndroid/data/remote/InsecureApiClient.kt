package com.weijia.vulndroid.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * InsecureApiClient — VulnDroid
 * ================================
 * SECURITY FINDING: M5 Insecure Communication — SSL/TLS Bypass
 *
 * OWASP Mobile Top 10 2024: M5
 * MASVS:  MSTG-NETWORK-3, MSTG-NETWORK-4
 * CWE:    CWE-295 (Improper Certificate Validation),
 *         CWE-297 (Improper Validation of Certificate with Host Mismatch)
 * Risk:   CRITICAL
 *
 * This client disables ALL SSL/TLS certificate validation.
 * This means:
 *
 * 1. Any certificate is accepted — including self-signed, expired, invalid certs
 * 2. Man-in-the-Middle (MITM) attacks are trivially possible:
 *    - Attacker creates a fake certificate for api.vulndroid-backend.com
 *    - Positions themselves between the app and the server (rogue Wi-Fi, ARP spoofing)
 *    - App accepts the fake certificate without complaint
 *    - Attacker sees all traffic in plaintext — tokens, passwords, PII
 *
 * 3. Combined with network_security_config.xml allowing cleartext HTTP:
 *    - HTTPS is broken (disabled cert validation)
 *    - HTTP is also used (cleartext allowed)
 *    - The app has NO secure communication at all
 *
 * ATTACK DEMONSTRATION:
 * 1. Configure Burp Suite on the same network
 * 2. Route emulator traffic through Burp (set proxy)
 * 3. No need to install Burp's CA cert — app accepts any cert
 * 4. All API calls (including auth tokens) visible in Burp's HTTP history
 *
 * REMEDIATION:
 * - Remove the custom TrustManager — use the system default
 * - Implement certificate pinning for production endpoints
 * - Use OkHttp's CertificatePinner for pinning
 * - Never ship an app that accepts all certificates
 */
object InsecureApiClient {

    private const val TAG = "VulnDroid_API"
    // M1 API key from strings.xml — used in every request header
    private const val API_KEY = "sk-prod-9f8a2b1c3d4e5f6g7h8i9j0k1l2m3n4"
    // M5 Base URL uses HTTP — not HTTPS
    private const val BASE_URL = "http://api.vulndroid-backend.com/v1/"

    /**
     * M5 VULNERABILITY: Custom TrustManager that accepts ALL certificates.
     *
     * This is the most dangerous SSL implementation possible.
     * The three empty override methods mean:
     * - checkClientTrusted: accepts any client cert (no client auth)
     * - checkServerTrusted: accepts any server cert (no validation at all)
     * - getAcceptedIssuers: returns empty array (no trusted CAs defined)
     *
     * This pattern is frequently copy-pasted from Stack Overflow
     * to "fix" SSL errors during development and accidentally shipped to production.
     */
    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            // M5 VULNERABILITY: No validation — accepts any client certificate
        }
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            // M5 VULNERABILITY: No validation — accepts any server certificate
            // This is what allows MITM attacks — attacker's cert is accepted here
            Log.w(TAG, "checkServerTrusted called — accepting without validation (INSECURE)")
        }
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    /**
     * Build an OkHttpClient with all SSL validation disabled.
     *
     * Additional issues:
     * - HttpLoggingInterceptor at BODY level logs all request/response bodies to Logcat
     *   including auth tokens, passwords, and PII
     * - No timeout configured — vulnerable to slow loris / resource exhaustion
     * - No certificate pinning
     */
    fun buildInsecureClient(): OkHttpClient {
        // M5 VULNERABILITY: SSLContext initialized with trust-all TrustManager
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())

        // M6 VULNERABILITY: Logging full request/response bodies to Logcat
        // Auth tokens, passwords, and PII are all logged at DEBUG level
        val loggingInterceptor = HttpLoggingInterceptor { message ->
            Log.d(TAG, message)  // Full HTTP body logged — tokens, PII, everything
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY  // BODY = log everything
        }

        return OkHttpClient.Builder()
            .sslSocketFactory(
                sslContext.socketFactory,
                trustAllCerts[0] as X509TrustManager  // M5 VULNERABILITY
            )
            // M5 VULNERABILITY: HostnameVerifier accepts any hostname
            // Without this, OkHttp would still check the hostname even with custom TrustManager
            .hostnameVerifier { hostname, session ->
                Log.w(TAG, "Accepting hostname without verification: $hostname")
                true  // Accept any hostname — MITM possible with any domain's cert
            }
            .addInterceptor(loggingInterceptor)
            .addInterceptor { chain ->
                // M1 VULNERABILITY: API key added to every request header
                val request = chain.request().newBuilder()
                    .addHeader("X-API-Key", API_KEY)
                    .addHeader("Authorization", "Bearer $API_KEY")
                    .build()
                Log.d(TAG, "Request with API key: ${request.url}")
                chain.proceed(request)
            }
            .build()
    }

    /**
     * Example API call — demonstrates cleartext HTTP + disabled SSL
     */
    fun fetchUserProfile(userId: String): String {
        val client = buildInsecureClient()
        // M5 VULNERABILITY: HTTP URL (not HTTPS) for API call
        val request = Request.Builder()
            .url("${BASE_URL}users/$userId")  // Cleartext HTTP
            .build()

        return try {
            val response = client.newCall(request).execute()
            // M4 VULNERABILITY: Response body used without validation
            response.body?.string() ?: ""
        } catch (e: Exception) {
            // M4 VULNERABILITY: Full exception message exposed to caller
            "Error: ${e.message}"
        }
    }
}
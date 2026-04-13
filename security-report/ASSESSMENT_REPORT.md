# VulnDroid — Mobile Security Assessment Report

**Classification:** Portfolio Demonstration — Educational Use Only
**Assessor:** Romain Bisschop
**Date:** March 2026
**App Version:** 1.0.0
**Package:** com.vulndroid
**Target:** Android 9.0 (API 28) — Android 13 (API 33)

---

## Executive Summary

VulnDroid is a deliberately vulnerable Android application assessed as part of a
mobile security portfolio project. The assessment identified **10 security findings**
across all OWASP Mobile Top 10 (2024) categories. The application demonstrates a
comprehensive attack surface spanning credential management, data storage, network
communication, cryptography, and application configuration.

**Overall Risk Rating: CRITICAL**

The combination of hardcoded credentials, disabled SSL validation, plaintext data
storage, SQL injection, and MD5 password hashing means a motivated attacker with
network access or physical device access could fully compromise the application and
its backend systems.

### Risk Summary

| Severity | Count |
|---|---|
| 🔴 Critical | 4 |
| 🟠 High | 4 |
| 🟡 Medium | 2 |
| **Total** | **10** |

### Tools Used

| Tool | Version | Purpose |
|---|---|---|
| MobSF | 3.9.7 | Automated static & dynamic analysis |
| apktool | 2.9.3 | APK decompilation |
| jadx | 1.5.0 | Java/Kotlin decompilation |
| Frida | 16.2.1 | Runtime instrumentation |
| Burp Suite Community | 2024.7 | Network traffic interception |
| adb | 34.0.5 | Device interaction & data extraction |
| SQLite3 | 3.43.0 | Database extraction and analysis |
| Objection | 1.11.0 | Runtime mobile exploration |

---

## Finding 1 — Hardcoded Credentials in APK Resources

**OWASP Mobile Top 10:** M1 — Improper Credential Usage
**MASVS:** MSTG-STORAGE-14
**CWE:** CWE-798 (Use of Hard-coded Credentials)
**Severity:** 🔴 Critical

### Description

Production API keys, admin credentials, database encryption keys, and a JWT signing
secret are embedded in plaintext within `res/values/strings.xml`. This file is
bundled inside the APK and can be extracted without any special tools or privileges.

### Steps to Reproduce

```bash
# Step 1: Download or copy the APK from the device
adb pull /data/app/com.vulndroid-1/base.apk vulndroid.apk

# Step 2: Decompile with apktool
apktool d vulndroid.apk -o vulndroid_decompiled

# Step 3: Read strings.xml — credentials visible immediately
cat vulndroid_decompiled/res/values/strings.xml
```

### Evidence

```xml
<!-- Output from strings.xml after decompilation -->
<string name="api_key">sk-prod-9f8a2b1c3d4e5f6g7h8i9j0k1l2m3n4</string>
<string name="admin_password">Admin@123!</string>
<string name="db_encryption_key">vulndroid-db-secret-2024</string>
<string name="jwt_secret">my-super-secret-jwt-signing-key-never-commit-this</string>
```

### Business Impact

An attacker who downloads the APK (publicly available on the Play Store) can
immediately extract valid production API credentials. These can be used to:
- Make authenticated API calls on behalf of any user
- Access the admin backend with hardcoded credentials
- Forge JWT tokens to impersonate any user
- Decrypt any data encrypted with the hardcoded key

### Remediation

```kotlin
// BAD — never do this
private const val API_KEY = "sk-prod-9f8a2b1c3d4e5f6g7h8i9j0k"

// GOOD — inject at build time from environment variables
val apiKey = BuildConfig.API_KEY  // Set in local.properties, never committed

// For runtime secrets — use Android Keystore
val keyStore = KeyStore.getInstance("AndroidKeyStore")
// Generate and store keys in hardware-backed secure enclave
```

**Additional actions:** Rotate all exposed credentials immediately. Add git-secrets
or truffleHog to CI/CD pipeline to prevent future commits of secrets.

---

## Finding 2 — SQL Injection in User Search and Authentication

**OWASP Mobile Top 10:** M4 — Insufficient Input/Output Validation
**MASVS:** MSTG-PLATFORM-2
**CWE:** CWE-89 (SQL Injection)
**Severity:** 🔴 Critical

### Description

The `VulnDroidDatabase` class constructs SQL queries via string concatenation without
any input sanitization or parameterized queries. This enables both data extraction
(dump all users) and authentication bypass (login as any user without a password).

### Attack 1 — Dump All Users

```
Input: ' OR '1'='1
Resulting query: SELECT * FROM users WHERE username LIKE '%' OR '1'='1%'
Result: Returns every row in the users table including hashed passwords and PII
```

### Attack 2 — Authentication Bypass

```
Username: admin' --
Password: anything

Resulting query:
  SELECT * FROM users WHERE username='admin' --' AND password='[md5_anything]'

The -- comments out the password check entirely.
Result: Logged in as admin with any password.
```

### Steps to Reproduce (Authentication Bypass)

1. Launch VulnDroid on emulator
2. Enter username: `admin' --`
3. Enter any password (e.g. `wrongpassword`)
4. Tap Sign In
5. Application logs in as admin — dashboard displays admin privileges

### Evidence

```
// Logcat output (from Log.d in VulnDroidDatabase.kt):
D/VulnDroid_DB: Auth query: SELECT * FROM users WHERE
                username='admin' --' AND password='[hash]'
```

### Business Impact

- Complete authentication bypass — attacker logs in as any user including admin
- Full database extraction — all user PII, password hashes, financial data
- Potential for data destruction via `DROP TABLE` injection

### Remediation

```kotlin
// BAD — string concatenation
val sql = "SELECT * FROM users WHERE username='$username' AND password='$password'"

// GOOD — parameterized query (prevents injection completely)
val sql = "SELECT * FROM users WHERE username=? AND password=?"
val cursor = db.rawQuery(sql, arrayOf(username, hashedPassword))
```

---

## Finding 3 — SSL/TLS Certificate Validation Disabled

**OWASP Mobile Top 10:** M5 — Insecure Communication
**MASVS:** MSTG-NETWORK-3, MSTG-NETWORK-4
**CWE:** CWE-295 (Improper Certificate Validation)
**Severity:** 🔴 Critical

### Description

`InsecureApiClient` implements a custom `X509TrustManager` that accepts all
certificates without validation. Combined with `network_security_config.xml`
permitting cleartext HTTP and trusting user-installed CA certificates, all network
traffic is trivially intercepted.

### Steps to Reproduce

```bash
# Setup: Configure Burp Suite proxy on same network as emulator
# Emulator proxy: 127.0.0.1:8080 (for adb reverse)

# No CA cert installation required — app accepts any certificate
# All HTTPS traffic is intercepted in plaintext by Burp Suite

# Captured in Burp HTTP History:
POST http://api.vulndroid-backend.com/v1/auth/login
X-API-Key: sk-prod-9f8a2b1c3d4e5f6g7h8i9j0k1l2m3n4
Content-Type: application/json

{"username":"john.doe","password":"password123"}
```

### Evidence

Burp Suite screenshot showing plaintext capture of login credentials and auth token
in HTTP history tab. (See: `/security-report/evidence/finding_03_burp_capture.png`)

### Remediation

```kotlin
// BAD — accepts all certificates
val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    // ...
})

// GOOD — use system default TrustManager
val client = OkHttpClient.Builder().build()  // Uses system CAs by default

// BETTER — add certificate pinning for sensitive endpoints
val client = OkHttpClient.Builder()
    .certificatePinner(
        CertificatePinner.Builder()
            .add("api.yourapp.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
            .build()
    )
    .build()
```

---

## Finding 4 — MD5 Password Hashing

**OWASP Mobile Top 10:** M10 — Insufficient Cryptography
**MASVS:** MSTG-CRYPTO-4
**CWE:** CWE-916 (Insufficient Password Hashing Iterations)
**Severity:** 🔴 Critical

### Description

Passwords are hashed using MD5 — a message digest algorithm designed for speed,
not security. MD5 is not a password hashing function. It provides no work factor
and is trivially reversed via rainbow tables.

### Exploit

```bash
# The hash in the database for "password123":
# 482c811da5d5b4bc6d497ffa98491e38

# Crack via online rainbow table (crackstation.net):
# Input:  482c811da5d5b4bc6d497ffa98491e38
# Output: password123
# Time:   < 1 second

# Or via hashcat on GPU:
hashcat -m 0 482c811da5d5b4bc6d497ffa98491e38 rockyou.txt
# Result: password123
# Time:   milliseconds on consumer GPU
```

### Evidence

Database extraction via adb showing MD5 hash column, followed by successful
cracking via CrackStation in under 1 second.

### Remediation

```kotlin
// BAD — MD5, no salt, no work factor
fun hashPassword(password: String): String {
    return MessageDigest.getInstance("MD5").digest(password.toByteArray()).toHex()
}

// GOOD — PBKDF2 with salt and work factor (delegate to backend ideally)
fun hashPasswordSecure(password: String, salt: ByteArray): ByteArray {
    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password.toCharArray(), salt, 600_000, 256)
    return factory.generateSecret(spec).encoded
}
// Even better: use bcrypt or Argon2 library on the backend server
```

---

## Finding 5 — Plaintext Sensitive Data in SharedPreferences

**OWASP Mobile Top 10:** M9 — Insecure Data Storage
**MASVS:** MSTG-STORAGE-1, MSTG-STORAGE-2
**CWE:** CWE-312 (Cleartext Storage of Sensitive Information)
**Severity:** 🟠 High

### Description

Authentication tokens, usernames, and user passwords are stored in plaintext
SharedPreferences. On debug builds and rooted devices, this file is accessible
without any special privileges via `adb shell run-as`.

### Steps to Reproduce

```bash
# Extract SharedPreferences on debug build (no root required)
adb shell run-as com.vulndroid cat /data/data/com.vulndroid/shared_prefs/user_session.xml
```

### Evidence

```xml
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<!-- Output of adb shell run-as com.vulndroid cat shared_prefs/user_session.xml -->
<map>
    <string name="username">john.doe</string>
    <string name="password">password123</string>       <!-- Plaintext password! -->
    <string name="auth_token">am9obi5kb2U6MTcwOTgxMjM0NTY3OA==</string>
    <boolean name="is_admin" value="false" />
    <boolean name="is_logged_in" value="true" />
</map>
```

### Remediation

```kotlin
// BAD — plaintext SharedPreferences
sharedPreferences.edit().putString("password", password).apply()

// GOOD — EncryptedSharedPreferences (Jetpack Security)
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()
val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "secure_session",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)
// Never store the plaintext password at all — only store the token
```

---

## Finding 6 — Exported Components Without Permission

**OWASP Mobile Top 10:** M8 — Security Misconfiguration
**MASVS:** MSTG-PLATFORM-1
**CWE:** CWE-926 (Improper Export of Android Application Components)
**Severity:** 🟠 High

### Description

Three application components are exported without requiring any permission:
`VulnerableDeepLinkActivity`, `TokenRefreshReceiver`, and `UserDataProvider`.
Any application on the device can interact with these components.

### Exploit — Activity Launch (Auth Bypass)

```bash
# Launch password reset activity directly — bypasses authentication
adb shell am start -n com.vulndroid/.ui.VulnerableDeepLinkActivity
# Result: Password reset screen opens without any authentication
```

### Exploit — ContentProvider Data Extraction

```bash
# Query all user data via exported ContentProvider — no permission required
adb shell content query --uri content://com.vulndroid.provider/users
# Result: All user records including email, phone, DOB, and MD5 hashes returned
```

### Remediation

```xml
<!-- BAD — exported with no protection -->
<activity android:name=".VulnerableDeepLinkActivity" android:exported="true" />

<!-- GOOD — not exported, or protected with custom signature permission -->
<activity android:name=".VulnerableDeepLinkActivity"
    android:exported="false" />

<!-- If deep links are needed: -->
<activity android:name=".DeepLinkActivity"
    android:exported="true"
    android:permission="com.vulndroid.permission.DEEP_LINK">
    <!-- Verify authentication state inside the Activity, not just permission -->
</activity>
```

---

## Finding 7 — AES/ECB Mode Encryption

**OWASP Mobile Top 10:** M10 — Insufficient Cryptography
**MASVS:** MSTG-CRYPTO-2
**CWE:** CWE-327 (Use of Broken Cryptographic Algorithm)
**Severity:** 🟠 High

### Description

`InsecureCryptoUtil.encryptECB()` uses AES in ECB mode. ECB encrypts each 16-byte
block independently, meaning identical plaintext blocks produce identical ciphertext
blocks. This reveals patterns in the data and provides no semantic security.

### Evidence

Encrypting the string "password123password123" in ECB mode:
```
Plaintext:  password123passw | ord123password12 | 3...
ECB output: [BLOCK_A][BLOCK_A][BLOCK_B]  ← Repeated blocks visible!
```

The repeated blocks reveal that the plaintext contains repeated content —
a classic ECB pattern attack allows partial plaintext recovery.

### Remediation

```kotlin
// BAD — ECB mode
val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")

// GOOD — GCM mode (provides both confidentiality and integrity/authenticity)
val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }  // Random IV per encrypt
val cipher = Cipher.getInstance("AES/GCM/NoPadding")
cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
// Prepend the IV to the ciphertext for storage
```

---

## Finding 8 — Credentials and PII Logged to Logcat

**OWASP Mobile Top 10:** M6 — Inadequate Privacy Controls
**MASVS:** MSTG-STORAGE-3
**CWE:** CWE-532 (Insertion of Sensitive Information into Log File)
**Severity:** 🟠 High

### Description

Username, password, auth queries, and API request/response bodies containing PII
are all written to Android system logs. Any application with the `READ_LOGS`
permission or adb access can read these.

### Steps to Reproduce

```bash
# Monitor logs while logging into VulnDroid
adb logcat | grep -i vulndroid

# Output:
D/VulnDroid_Login: Login attempt — username: john.doe, password: password123
D/VulnDroid_DB: Auth query: SELECT * FROM users WHERE username='john.doe' AND password='[hash]'
D/VulnDroid_API: POST http://api.vulndroid-backend.com/v1/auth
D/VulnDroid_API: {"username":"john.doe","password":"password123","token":"Bearer abc123"}
```

### Remediation

```kotlin
// BAD — logging sensitive data
Log.d(TAG, "Login attempt — username: $username, password: $password")

// GOOD — never log credentials. For debugging, log only non-sensitive context
Log.d(TAG, "Login attempt for user ID: ${userId.take(4)}***")

// For production builds — strip all debug logs at compile time
if (BuildConfig.DEBUG) {
    Log.d(TAG, "Debug info here")
}
// Or use ProGuard rule: -assumenosideeffects class android.util.Log { *; }
```

---

## Finding 9 — Insecure Data Backup (allowBackup=true)

**OWASP Mobile Top 10:** M8 — Security Misconfiguration
**MASVS:** MSTG-STORAGE-8
**CWE:** CWE-530 (Exposure of Backup File to Unauthorized Control)
**Severity:** 🟡 Medium

### Description

`android:allowBackup="true"` in AndroidManifest.xml permits full application data
extraction via `adb backup` — no root required on debug builds.

### Steps to Reproduce

```bash
# Extract full app data backup — no root required on debug build
adb backup -f vulndroid_backup.ab com.vulndroid

# Convert Android backup to tar archive
java -jar abe.jar unpack vulndroid_backup.ab vulndroid_backup.tar

# Extract and explore
tar -xf vulndroid_backup.tar
# Contents:
# apps/com.vulndroid/sp/user_session.xml  ← SharedPreferences with token/password
# apps/com.vulndroid/db/vulndroid.db      ← Full SQLite database
# apps/com.vulndroid/f/vulndroid_debug.log ← External log file with credentials
```

### Remediation

```xml
<!-- BAD -->
<application android:allowBackup="true"/>

<!-- GOOD — disable backup entirely -->
<application android:allowBackup="false"/>

<!-- OR — define explicit backup rules to exclude sensitive files -->
<application
    android:allowBackup="true"
    android:fullBackupContent="@xml/backup_rules"/>
```

```xml
<!-- res/xml/backup_rules.xml -->
<full-backup-content>
    <exclude domain="sharedpref" path="user_session.xml" />
    <exclude domain="database" path="vulndroid.db" />
</full-backup-content>
```

---

## Finding 10 — Excessive Permissions

**OWASP Mobile Top 10:** M6 — Inadequate Privacy Controls
**MASVS:** MSTG-PLATFORM-1
**CWE:** CWE-250 (Execution with Unnecessary Privileges)
**Severity:** 🟡 Medium

### Description

The application requests 10 permissions, of which at least 4 are unnecessary for
the core functionality of a notes application: `READ_CALL_LOG`, `RECORD_AUDIO`,
`READ_PHONE_STATE`, and `ACCESS_FINE_LOCATION`.

### Evidence

```xml
<!-- Permissions from AndroidManifest.xml — unnecessary ones: -->
<uses-permission android:name="android.permission.READ_CALL_LOG" />   <!-- WHY? -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />     <!-- WHY? -->
<uses-permission android:name="android.permission.READ_PHONE_STATE" /> <!-- WHY? -->
```

### Business Impact

- User trust erosion — users question why a notes app needs call log access
- Play Store policy violation — excessive permissions can trigger removal
- Privacy regulation exposure — GDPR/PDPA may require justification for each permission
- Increased attack surface — each permission is a capability available to the app's
  attack surface if the app is compromised

### Remediation

Request only the permissions absolutely necessary for core features.
Review each permission against actual feature requirements. Remove all others.

---

## Summary Table

| Finding | OWASP ID | Severity | File |
|---|---|---|---|
| Hardcoded credentials | M1 | 🔴 Critical | strings.xml, LoginActivity.kt |
| SQL Injection | M4 | 🔴 Critical | VulnDroidDatabase.kt |
| Disabled SSL validation | M5 | 🔴 Critical | InsecureApiClient.kt |
| MD5 password hashing | M10 | 🔴 Critical | InsecureCryptoUtil.kt |
| Plaintext SharedPreferences | M9 | 🟠 High | LoginActivity.kt |
| Exported components | M8 | 🟠 High | AndroidManifest.xml |
| ECB mode encryption | M10 | 🟠 High | InsecureCryptoUtil.kt |
| Credentials in Logcat | M6 | 🟠 High | LoginActivity.kt, VulnDroidDatabase.kt |
| allowBackup=true | M8 | 🟡 Medium | AndroidManifest.xml |
| Excessive permissions | M6 | 🟡 Medium | AndroidManifest.xml |

---

## Assessor Notes

This assessment was conducted as a portfolio project to demonstrate mobile security
research methodology. All vulnerabilities were intentionally introduced and are
fully understood at the code level — not discovered blindly via tooling.

The assessment demonstrates competency in:
- Static analysis (apktool, jadx, manual code review)
- Dynamic analysis (Frida, adb, Burp Suite)
- OWASP Mobile Top 10 (2024) full coverage
- Professional penetration test report writing
- Remediation guidance with production-ready code examples
- Android internals knowledge (SharedPreferences, SQLiteOpenHelper, OkHttp, Manifest)

**Romain Bisschop**
Senior Android Developer | Mobile Security Researcher
CompTIA Security+ | CAISP (In Progress)

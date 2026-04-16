package com.weijia.vulndroid.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

/**
 * VulnDroidDatabase — Insecure SQLite Implementation
 * ====================================================
 * SECURITY FINDINGS IN THIS FILE:
 *
 * M4 Insufficient Input/Output Validation — SQL Injection
 *      The searchUsers() and getUserByCredentials() methods build SQL queries
 *      via raw string concatenation. Any input is directly embedded into the query.
 *
 *      Attack on searchUsers():
 *        Input: ' OR '1'='1
 *        Query becomes: SELECT * FROM users WHERE username LIKE '%' OR '1'='1%'
 *        Result: Returns ALL users from the database
 *
 *      Attack on getUserByCredentials() — Authentication Bypass:
 *        Username: admin' --
 *        Password: anything
 *        Query becomes: SELECT * FROM users WHERE username='admin' --' AND password='anything'
 *        The -- comments out the password check — logs in as admin with any password
 *
 * M9 Insecure Data Storage — Plaintext sensitive data in SQLite
 *      Passwords stored as MD5 hashes (broken) — see InsecureCryptoUtil.
 *      Notes column stores content in plaintext with no encryption.
 *      Database file at: /data/data/com.weijia.vulndroid/databases/vulndroid.db
 *      Extraction: `adb shell run-as com.weijia.vulndroid sqlite3 databases/vulndroid.db .dump`
 *
 * M9 No database encryption
 *      Standard SQLiteOpenHelper creates an unencrypted database file.
 *      The db_encryption_key in strings.xml is referenced but never actually used.
 *      FIX: Use SQLCipher or EncryptedSharedPreferences equivalent for SQLite.
 */
class VulnDroidDatabase(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val TAG = "VulnDroid_DB"
        private const val DB_NAME = "vulndroid.db"   // M9 Unencrypted database
        private const val DB_VERSION = 1

        // Table and column names — visible in decompiled APK
        const val TABLE_USERS = "users"
        const val COL_ID = "id"
        const val COL_USERNAME = "username"
        const val COL_PASSWORD = "password"   // M9 Stores MD5 hash — not bcrypt/Argon2
        const val COL_EMAIL = "email"
        const val COL_PHONE = "phone"         // M6 PII stored in plaintext
        const val COL_DOB = "date_of_birth"   // M6 PII stored in plaintext
        const val COL_CREDIT_CARD = "credit_card_last4" // M6 Financial data in plaintext DB
        const val COL_IS_ADMIN = "is_admin"
        const val COL_SESSION_TOKEN = "session_token"

        const val TABLE_NOTES = "notes"
        const val COL_NOTE_ID = "id"
        const val COL_NOTE_OWNER = "owner_username"
        const val COL_NOTE_CONTENT = "content"   // M9 Sensitive notes stored unencrypted
    }

    override fun onCreate(db: SQLiteDatabase) {
        // M9 VULNERABILITY: Schema stores sensitive PII in plaintext columns
        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USERNAME TEXT NOT NULL UNIQUE,
                $COL_PASSWORD TEXT NOT NULL,
                $COL_EMAIL TEXT,
                $COL_PHONE TEXT,
                $COL_DOB TEXT,
                $COL_CREDIT_CARD TEXT,
                $COL_IS_ADMIN INTEGER DEFAULT 0,
                $COL_SESSION_TOKEN TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_NOTES (
                $COL_NOTE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_NOTE_OWNER TEXT NOT NULL,
                $COL_NOTE_CONTENT TEXT NOT NULL
            )
        """.trimIndent())

        // Seed data — demonstrates what is extractable from the DB
        seedDatabase(db)
    }

    private fun seedDatabase(db: SQLiteDatabase) {
        // M9 MD5 hash of "password123" — trivially cracked via rainbow tables
        val weakHash = "482c811da5d5b4bc6d497ffa98491e38"
        db.execSQL("""
            INSERT INTO $TABLE_USERS
            ($COL_USERNAME, $COL_PASSWORD, $COL_EMAIL, $COL_PHONE, $COL_DOB, $COL_CREDIT_CARD, $COL_IS_ADMIN)
            VALUES ('john.doe', '$weakHash', 'john@example.com', '+66-81-234-5678', '1990-05-15', '4242', 0)
        """)

        db.execSQL("""
            INSERT INTO $TABLE_USERS
            ($COL_USERNAME, $COL_PASSWORD, $COL_EMAIL, $COL_PHONE, $COL_DOB, $COL_CREDIT_CARD, $COL_IS_ADMIN)
            VALUES ('admin', '$weakHash', 'admin@vulndroid.com', '+1-555-000-0001', '1985-01-01', '1337', 1)
        """)

        db.execSQL("""
            INSERT INTO $TABLE_USERS
            ($COL_USERNAME, $COL_PASSWORD, $COL_EMAIL, $COL_PHONE, $COL_DOB, $COL_CREDIT_CARD, $COL_IS_ADMIN)
            VALUES ('admin@vulndroid.com', '$weakHash', 'admin@vulndroid.com', '+1-555-000-0001', '1985-01-01', '1337', 1)
        """)
        db.execSQL("""
            INSERT INTO $TABLE_NOTES ($COL_NOTE_OWNER, $COL_NOTE_CONTENT)
            VALUES ('admin', 'Server root password: Sup3rS3cr3t!2024')
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_USERS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NOTES")
        onCreate(db)
    }

    /**
     * M4 VULNERABILITY: SQL Injection via string concatenation.
     *
     * SAFE version uses parameterized queries:
     *   db.rawQuery("SELECT * FROM users WHERE username LIKE ?", arrayOf("%$query%"))
     *
     * UNSAFE version (below) concatenates directly:
     *   Input: ' OR '1'='1  →  dumps entire users table
     *   Input: '; DROP TABLE users; --  →  destroys data
     */
    fun searchUsers(query: String): List<Map<String, String>> {
        val results = mutableListOf<Map<String, String>>()
        val db = readableDatabase

        // M4 VULNERABILITY: Raw string concatenation — SQL injection possible
        val sql = "SELECT * FROM $TABLE_USERS WHERE $COL_USERNAME LIKE '%$query%'"
        Log.d(TAG, "Executing query: $sql")  // M6 VULNERABILITY: Logging raw SQL with user input

        try {
            val cursor = db.rawQuery(sql, null)
            while (cursor.moveToNext()) {
                val row = mutableMapOf<String, String>()
                for (i in 0 until cursor.columnCount) {
                    row[cursor.getColumnName(i)] = cursor.getString(i) ?: ""
                }
                results.add(row)
            }
            cursor.close()
        } catch (e: Exception) {
            // M4 VULNERABILITY: Exposing raw SQL error to the UI — reveals schema
            Log.e(TAG, "SQL Error (exposing to user): ${e.message}")
            throw RuntimeException("Database error: ${e.message}") // Full error shown in UI
        }
        return results
    }

    /**
     * M3 + M4 VULNERABILITY: Authentication bypass via SQL injection.
     *
     * Attack:
     *   username = admin' --
     *   password = anything
     *
     * Resulting query:
     *   SELECT * FROM users WHERE username='admin' --' AND password='md5_anything'
     *   The -- comments out the password check entirely.
     *
     * SAFE version:
     *   db.rawQuery("SELECT * FROM users WHERE username=? AND password=?",
     *               arrayOf(username, hashedPassword))
     */
    fun getUserByCredentials(username: String, hashedPassword: String): Map<String, String>? {
        val db = readableDatabase

        // M4 VULNERABILITY: String concatenation in auth query
        val sql = "SELECT * FROM $TABLE_USERS WHERE " +
                "$COL_USERNAME='$username' AND $COL_PASSWORD='$hashedPassword'"
        Log.d(TAG, "Auth query: $sql")  // M6 VULNERABILITY: Auth query with hash logged

        val cursor = db.rawQuery(sql, null)
        return if (cursor.moveToFirst()) {
            val row = mutableMapOf<String, String>()
            for (i in 0 until cursor.columnCount) {
                row[cursor.getColumnName(i)] = cursor.getString(i) ?: ""
            }
            cursor.close()
            row
        } else {
            cursor.close()
            null
        }
    }

    /**
     * M9 Retrieve notes without access control check.
     * M3 VULNERABILITY: No authorization — any user can retrieve any user's notes
     *      by passing a different ownerUsername. No check that the caller is the owner.
     */
    fun getNotesForUser(ownerUsername: String): List<String> {
        val db = readableDatabase
        // M4 SQL injection also possible here
        val sql = "SELECT $COL_NOTE_CONTENT FROM $TABLE_NOTES WHERE $COL_NOTE_OWNER='$ownerUsername'"
        val cursor = db.rawQuery(sql, null)
        val notes = mutableListOf<String>()
        while (cursor.moveToNext()) {
            notes.add(cursor.getString(0))
        }
        cursor.close()
        return notes
    }
}
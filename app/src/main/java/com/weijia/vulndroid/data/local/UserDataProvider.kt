package com.weijia.vulndroid.data.local

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri

/**
 * UserDataProvider — M8: Security Misconfiguration
 * =================================================
 * Exported ContentProvider with NO read or write permission.
 * Declared in AndroidManifest.xml with:
 *   android:exported="true"
 *   android:readPermission=""    ← empty = no permission required
 *   android:writePermission=""
 *
 * Any app on the device can query all user data:
 *   content query --uri content://com.vulndroid.provider/users
 *   content query --uri content://com.vulndroid.provider/notes
 *
 * Via ADB (no root needed on debug build):
 *   adb shell content query --uri content://com.vulndroid.provider/users
 *
 * IMPACT: Full database exposure — emails, phone numbers, MD5 hashes,
 * credit card last4, date of birth, session tokens. All returned to
 * any caller with zero authentication.
 *
 * REMEDIATION:
 *   android:exported="false"
 *   OR android:readPermission="com.vulndroid.permission.READ_DATA"
 *      (with protectionLevel="signature" so only VulnDroid can hold it)
 */
class UserDataProvider : ContentProvider() {

    companion object {
        private const val TAG = "VulnDroid_Provider"
        const val AUTHORITY = "com.vulndroid.provider"

        // URI patterns
        private const val USERS = 1
        private const val NOTES = 2

        private val uriMatcher = UriMatcher(UriMatcher.NO_MATCH).apply {
            addURI(AUTHORITY, "users", USERS)
            addURI(AUTHORITY, "notes", NOTES)
        }
    }

    private lateinit var db: VulnDroidDatabase

    override fun onCreate(): Boolean {
        db = VulnDroidDatabase(context!!)
        Log.d(TAG, "UserDataProvider initialised — exported with no permission (M8)")
        return true
    }

    /**
     * M8 VULNERABILITY: No caller identity check.
     * getCallingPackage() would reveal who is querying — but we never check it.
     * Any package gets the full result set.
     *
     * M3 VULNERABILITY: No authorization — caller receives all rows,
     * not just rows belonging to the authenticated user.
     */
    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        val caller = callingPackage ?: "unknown"
        // [M6] VULNERABILITY: Caller package logged — but never actually checked
        Log.w(TAG, "Content provider query from: $caller | uri: $uri")

        val readableDb = db.readableDatabase
        return when (uriMatcher.match(uri)) {
            USERS -> {
                // [M8] Returns ALL users to ANY caller — no permission, no filter
                readableDb.rawQuery(
                    "SELECT * FROM ${VulnDroidDatabase.TABLE_USERS}",
                    null
                )
            }
            NOTES -> {
                // [M3] Returns ALL notes regardless of caller identity
                readableDb.rawQuery(
                    "SELECT * FROM ${VulnDroidDatabase.TABLE_NOTES}",
                    null
                )
            }
            else -> null
        }
    }

    override fun getType(uri: Uri): String? = null

    /**
     * M8 Insert also unprotected — any app can write user records
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        Log.w(TAG, "Unprotected insert from: ${callingPackage} | uri: $uri")
        if (values == null) return null
        val writableDb = db.writableDatabase
        return when (uriMatcher.match(uri)) {
            USERS -> {
                writableDb.insert(VulnDroidDatabase.TABLE_USERS, null, values)
                "content://$AUTHORITY/users".toUri()
            }
            else -> null
        }
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int = 0
}
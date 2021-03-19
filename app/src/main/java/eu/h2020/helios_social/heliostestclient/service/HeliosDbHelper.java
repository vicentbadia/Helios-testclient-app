package eu.h2020.helios_social.heliostestclient.service;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

//
// Message entries are cached locally to SQLite database. Database schema is described in
// HeliosDbContract class. Modifications to the database schema should also increment the
// version number. This is just a message cache so all upgrades and downgrades will just
// remove the history.
// More information from:
//   https://developer.android.com/training/data-storage/sqlite
//
public class HeliosDbHelper extends SQLiteOpenHelper {
    public static final int DATABASE_VERSION = 3;
    public static final String DATABASE_NAME = "HeliosChatMessages.db";

    public HeliosDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(HeliosDbContract.SQL_CREATE_ENTRIES);
    }
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(HeliosDbContract.SQL_DELETE_ENTRIES);
        onCreate(db);
    }
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

}

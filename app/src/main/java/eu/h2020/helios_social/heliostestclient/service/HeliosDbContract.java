package eu.h2020.helios_social.heliostestclient.service;

import android.provider.BaseColumns;

public final class HeliosDbContract {

    // This class is never instantiated. Adding private constructor to prevent that.
    private HeliosDbContract() {
    }

    // The cache database table name and columns of the table
    public static class HeliosEntry implements BaseColumns {
        public static final String TABLE_NAME = "mytestdb";
        public static final String COLUMN_MESSAGE_TYPE = "messageType";
        public static final String COLUMN_MESSAGE = "msg";
        public static final String COLUMN_MESSAGE_RECEIVED = "msgReceived";
        public static final String COLUMN_SENDER_NAME = "senderName";
        public static final String COLUMN_SENDER_UUID = "senderUUID";
        public static final String COLUMN_TOPIC = "topic";
        public static final String COLUMN_TIMESTAMP = "ts";
        public static final String COLUMN_MESSAGE_UUID = "uuid";
        public static final String COLUMN_MILLISECS = "millisecs";
        public static final String COLUMN_MEDIA_FILENAME = "mediaFilename";
    }

    // SQLite SQL statement to create a database table
    public static final String SQL_CREATE_ENTRIES =
            "CREATE TABLE " + HeliosEntry.TABLE_NAME + " (" +
                    HeliosEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                    HeliosEntry.COLUMN_MESSAGE_TYPE + " INTEGER," +
                    HeliosEntry.COLUMN_MESSAGE + " TEXT," +
                    HeliosEntry.COLUMN_MESSAGE_RECEIVED + " INTEGER," +
                    HeliosEntry.COLUMN_SENDER_NAME + " TEXT," +
                    HeliosEntry.COLUMN_SENDER_UUID + " TEXT," +
                    HeliosEntry.COLUMN_TOPIC + " TEXT," +
                    HeliosEntry.COLUMN_TIMESTAMP + " TEXT," +
                    HeliosEntry.COLUMN_MESSAGE_UUID + " TEXT," +
                    HeliosEntry.COLUMN_MILLISECS + " TEXT," +
                    HeliosEntry.COLUMN_MEDIA_FILENAME + " TEXT)";

    // SQLite SQL statement to remove a database table
    public static final String SQL_DELETE_ENTRIES =
            "DROP TABLE IF EXISTS " + HeliosEntry.TABLE_NAME;

}
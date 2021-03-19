package eu.h2020.helios_social.heliostestclient.service;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.google.gson.JsonSyntaxException;

import org.threeten.bp.format.DateTimeParseException;

import java.time.ZonedDateTime;
import java.util.ArrayList;

import eu.h2020.helios_social.heliostestclient.data.HeliosMessagePart;
import eu.h2020.helios_social.heliostestclient.data.JsonMessageConverter;

/**
 * This class takes care of storing and retrieving HELIOS messages from SQLite database.
 */
public class ChatMessageStore {
    private static final String TAG = "ChatMessageStore";
    private HeliosDbHelper mDbHelper;
    private SQLiteDatabase mDbWritable;
    private SQLiteDatabase mDbReadable;
    private final static String HELIOS_JSON_KW_MSG_TYPE = "messageType";
    private final static String HELIOS_JSON_KW_MSG = "msg";
    private final static String HELIOS_JSON_KW_MSG_RECEIVED = "msgReceived";
    private final static String HELIOS_JSON_KW_SENDER_NAME = "senderName";
    private final static String HELIOS_JSON_KW_SENDER_UUID = "senderUUID";
    private final static String HELIOS_JSON_KW_TOPIC = "to";
    private final static String HELIOS_JSON_KW_TIMESTAMP = "ts";
    private final static String HELIOS_JSON_KW_MSG_UUID = "uuid";
    private final static String HELIOS_JSON_KW_MEDIA_FILENAME = "mediaFileName";

    public ChatMessageStore(Context ctx) {
        mDbHelper = new HeliosDbHelper(ctx);
        mDbWritable = mDbHelper.getWritableDatabase();
        mDbReadable = mDbHelper.getReadableDatabase();
    }

    /**
     * Add HELIOS message to SQLite storage
     *
     * @param message HELIOS message
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void addMessage(HeliosMessagePart message) {
        ContentValues values = new ContentValues();
        long millis;
        // This test is a workaround to a case if somebody is sending a message without
        // a message type using the old version of the client.
        if (message.messageType == null) {
            values.put(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_TYPE, 0);
        } else {
            values.put(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_TYPE, message.messageType.ordinal());
        }
        // Extract Epoch milliseconds as long int from ZonedDateTime text format
        if (message.ts != null) {
            try {
                millis = ZonedDateTime.parse(message.ts).toInstant().toEpochMilli();
            } catch (DateTimeParseException e) {
                millis = ZonedDateTime.now().toInstant().toEpochMilli();
                Log.d(TAG, "Cannot parse timestamp - setting as current time");
            }
        } else {
            millis = ZonedDateTime.now().toInstant().toEpochMilli();
            Log.d(TAG, "Timestamp missing - setting as current time");
        }
        values.put(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE, message.msg);
        values.put(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_RECEIVED, message.msgReceived ? 1 : 0);
        values.put(HeliosDbContract.HeliosEntry.COLUMN_SENDER_NAME, message.senderName);
        values.put(HeliosDbContract.HeliosEntry.COLUMN_SENDER_UUID, message.senderUUID);
        values.put(HeliosDbContract.HeliosEntry.COLUMN_TOPIC, message.to);
        values.put(HeliosDbContract.HeliosEntry.COLUMN_TIMESTAMP, message.ts);
        values.put(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_UUID, message.getUuid());
        values.put(HeliosDbContract.HeliosEntry.COLUMN_MILLISECS, millis);
        if (message.mediaFileName != null) {
            values.put(HeliosDbContract.HeliosEntry.COLUMN_MEDIA_FILENAME, message.mediaFileName);
        } else {
            values.putNull(HeliosDbContract.HeliosEntry.COLUMN_MEDIA_FILENAME);
        }

        try {
            long newRowId = mDbWritable.insert(HeliosDbContract.HeliosEntry.TABLE_NAME, null, values);
            Log.d(TAG, "Add new entry to SQLite store. Index = " + newRowId);
        } catch (Exception e) {
            Log.e(TAG, "Add new entry to SQLite store. error = " + e);
        }

    }

    /**
     * Build a JSON entry from key value string pairs.
     *
     * @param key JSON key value string
     * @param value Value of the field as string
     * @return New JSON entry
     */
     private String buildJsonStrEntry(String key, String value) {
         return "\"" + key + "\"" + ":" + "\"" + value + "\"";
     }

    /**
     * Build a JSON entry from a key and a boolean value.
     *
     * @param key JSON key value string
     * @param value Boolean value 0 => false, >0 => true
     * @return New JSON entry
     */
     private String buildJsonBoolEntry(String key, long value) {
         return "\"" + key + "\"" + ":" + ((value > 0) ? "true" : "false");
     }

    /**
     * Build a JSON entry to HeliosMessage message type which is enum.
     * TODO: Better way to match values in HeliosMessagePart.
     *
     * @param key Message type key
     * @param value Enum value as long
     * @return New JSON entry
     */
     private String buildJsonMsgTypeEntry(String key, long value) {
         String str;
         switch ((int)value) {
             case 0:
                 str = "MESSAGE";
                 break;
             case 1:
                 str = "HEARTBEAT";
                 break;
             case 2:
                 str = "JOIN";
                 break;
             case 3:
                 str = "LEAVE";
                 break;
             case 4:
                 str = "PUBSUB_SYNC_RESEND";
                 break;
             default:
                 Log.e(TAG, "Unsupported message type " + value);
                 return null;
         }
         String s = buildJsonStrEntry(key, str);
         return s;
     }

    /**
     * Process database table row. Extract different fields and then create a JSON message that
     * represents the message.
     *
     * @param cursor Database query cursor
     * @return Helios message entry as a JSONmessage
     */
     private String buildJsonMsgFromDbRow(Cursor cursor) {
         String mediaFileName = null;
         long id = cursor.getLong(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry._ID));
         long messageType = cursor.getLong(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_TYPE));
         String msg = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE));
         long msgReceived = cursor.getLong(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_RECEIVED));
         String senderName = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_SENDER_NAME));
         String senderUuid = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_SENDER_UUID));
         String topic = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_TOPIC));
         String timestamp = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_TIMESTAMP));
         String uuid = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_MESSAGE_UUID));
         if (!cursor.isNull(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_MEDIA_FILENAME))) {
             mediaFileName = cursor.getString(cursor.getColumnIndexOrThrow(HeliosDbContract.HeliosEntry.COLUMN_MEDIA_FILENAME));
         }

         String s = "{" +
                 buildJsonMsgTypeEntry(HELIOS_JSON_KW_MSG_TYPE, messageType) + "," +
                 buildJsonStrEntry(HELIOS_JSON_KW_MSG, msg) + "," +
                 ((mediaFileName == null) ? "" : buildJsonStrEntry(HELIOS_JSON_KW_MEDIA_FILENAME, mediaFileName) + ",") +
                 buildJsonBoolEntry(HELIOS_JSON_KW_MSG_RECEIVED, msgReceived) + "," +
                 buildJsonStrEntry(HELIOS_JSON_KW_SENDER_NAME, senderName) + "," +
                 buildJsonStrEntry(HELIOS_JSON_KW_SENDER_UUID, senderUuid) + "," +
                 buildJsonStrEntry(HELIOS_JSON_KW_TOPIC, topic) + "," +
                 buildJsonStrEntry(HELIOS_JSON_KW_TIMESTAMP, timestamp) + "," +
                 buildJsonStrEntry(HELIOS_JSON_KW_MSG_UUID, uuid) +
                 "}";
          return s;
     }

    /**
     * Load HELIOS messages from SQLite database
     *
     * @param topic Topic name to be requested.
     * @return Array list of HeliosMessagePart objects
     */
     public ArrayList<HeliosMessagePart> loadMessages(String topic) {
         ArrayList<HeliosMessagePart> messages = new ArrayList<HeliosMessagePart>();
         String sortOrder = HeliosDbContract.HeliosEntry.COLUMN_TIMESTAMP + " ASC";
         JsonMessageConverter jconv = JsonMessageConverter.getInstance();
         String selection = HeliosDbContract.HeliosEntry.COLUMN_TOPIC + " = ?";
         String[] selectionArgs = new String[1];
         selectionArgs[0] = topic;

         Cursor cursor = mDbReadable.query(
                HeliosDbContract.HeliosEntry.TABLE_NAME,   // The table to query
                null,                              // The array of columns to return (pass null to get all)
                selection,                                 // The columns for the WHERE clause
                selectionArgs,                             // The values for the WHERE clause
                null,                              // don't group the rows
                null,                               // don't filter by row groups
                sortOrder                                  // The sort order
         );
         while (cursor.moveToNext()) {
             String heliosJsonMsg = buildJsonMsgFromDbRow(cursor);
             try {
                 HeliosMessagePart parsedMsg = jconv.readHeliosMessagePart(heliosJsonMsg);
                 messages.add(parsedMsg);
             } catch (JsonSyntaxException e) {
                 Log.d(TAG, "Skip malformed JSON message\n" + heliosJsonMsg);
             }
         }
         cursor.close();
         return messages;
    }

    /**
     * Load HELIOS messages from SQLite database. This will match two topics.
     *
     * @param topic1 Topic name to be requested.
     * @param topic2 Alternative topic name
     * @return Array list of HeliosMessagePart objects
     */
    public ArrayList<HeliosMessagePart> loadMessages(String topic1, String topic2) {
        ArrayList<HeliosMessagePart> messages = new ArrayList<HeliosMessagePart>();
        String sortOrder = HeliosDbContract.HeliosEntry.COLUMN_TIMESTAMP + " ASC";
        JsonMessageConverter jconv = JsonMessageConverter.getInstance();
        String selection = HeliosDbContract.HeliosEntry.COLUMN_TOPIC + " = ? OR " +
                HeliosDbContract.HeliosEntry.COLUMN_TOPIC + " = ?";
        String[] selectionArgs = new String[] {topic1, topic2};

        Cursor cursor = mDbReadable.query(
                HeliosDbContract.HeliosEntry.TABLE_NAME,   // The table to query
                null,                              // The array of columns to return (pass null to get all)
                selection,                                 // The columns for the WHERE clause
                selectionArgs,                             // The values for the WHERE clause
                null,                              // don't group the rows
                null,                               // don't filter by row groups
                sortOrder                                  // The sort order
        );
        while (cursor.moveToNext()) {
            String heliosJsonMsg = buildJsonMsgFromDbRow(cursor);
            try {
                HeliosMessagePart parsedMsg = jconv.readHeliosMessagePart(heliosJsonMsg);
                messages.add(parsedMsg);
            } catch (JsonSyntaxException e) {
                Log.d(TAG, "Skip malformed JSON message:\n" + heliosJsonMsg);
            }
        }
        cursor.close();
        return messages;
    }

    public ArrayList<HeliosMessagePart> dumpDB() {
        ArrayList<HeliosMessagePart> messages = new ArrayList<HeliosMessagePart>();
        String sortOrder = HeliosDbContract.HeliosEntry._ID + " ASC";
        JsonMessageConverter jconv = JsonMessageConverter.getInstance();

        Cursor cursor = mDbReadable.query(
                HeliosDbContract.HeliosEntry.TABLE_NAME,   // The table to query
                null,                              // The array of columns to return (pass null to get all)
                null,                                 // The columns for the WHERE clause
                null,                             // The values for the WHERE clause
                null,                              // don't group the rows
                null,                               // don't filter by row groups
                null                                  // The sort order
        );
        while (cursor.moveToNext()) {
            String heliosJsonMsg = buildJsonMsgFromDbRow(cursor);
            try {
                HeliosMessagePart parsedMsg = jconv.readHeliosMessagePart(heliosJsonMsg);
                Log.d(TAG, "Message: " + heliosJsonMsg);
                messages.add(parsedMsg);
            } catch (JsonSyntaxException e) {
                Log.d(TAG, "BAD message: " + heliosJsonMsg);
            }
        }
        cursor.close();
        return messages;
    }

    /**
     * Delete expired database row entries that are older than the threshold time that is
     * given as a parameter.
     *
     * @param millis Expiration threshold time as milliseconds from Epoch
     * @return Number of rows removed or zero
     */
    public int deleteExpiredEntries(long millis) {
        int n;
        String selection = HeliosDbContract.HeliosEntry.COLUMN_MILLISECS + " < ?";
        String[] selectionArgs = new String[] { String.valueOf(millis) };
        n = mDbWritable.delete(HeliosDbContract.HeliosEntry.TABLE_NAME, selection, selectionArgs);
        if (n > 0) {
            Log.d(TAG, "" + n + " expired rows deleted");
        }
        return n;
    }

    /**
     * Close database connection
     */
    public void closeDatabase() {
        mDbHelper.close();
    }
}

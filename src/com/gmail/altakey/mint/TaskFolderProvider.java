package com.gmail.altakey.mint;

import android.content.Context;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.net.Uri;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.database.sqlite.SQLiteStatement;
import android.content.UriMatcher;
import android.content.ContentUris;
import android.database.Cursor;

import java.util.Arrays;
import java.util.List;

public class TaskFolderProvider extends ContentProvider {
    public static final Uri CONTENT_URI = Uri.parse(String.format("content://%s/folders", ProviderMap.AUTHORITY_FOLDER));

    public static final String[] PROJECTION = new String[] {
        "id", "name", "private", "archived", "ord"
    };

    public static final String DEFAULT_ORDER = "order by name";
    public static final String NO_ORDER = "";
    public static final String ID_FILTER = "id=?";

    public static final String COLUMN_ID = "id";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_PRIVATE = "private";
    public static final String COLUMN_ARCHIVED = "archived";
    public static final String COLUMN_ORD = "ord";

    public static final int COL_ID = 0;
    public static final int COL_NAME = 1;
    public static final int COL_PRIVATE = 2;
    public static final int COL_ARCHIVED = 3;
    public static final int COL_ORD = 4;

    private static final String FOLDER_QUERY = "SELECT id,name,private,archived,ord FROM folders WHERE %s %s";

    private static final String FOLDER_INSERT_QUERY = "INSERT INTO folders (name,private,archived,ord) VALUES (?,?,?,?)";

    private static final String FOLDER_REPLACE_QUERY = "REPLACE INTO folders (id,name,private,archived,ord) VALUES (?,?,?,?,?)";

    private static final String FOLDER_UPDATE_QUERY = "UPDATE folders set name=?,private=?,archived=?,ord=? %s";

    private static final String FOLDER_DELETE_QUERY = "DELETE folders %s";

    private SQLiteOpenHelper mHelper;

    @Override
    public String getType(Uri uri) {
        return new ProviderMap(uri).getContentType();
    }

    @Override
    public boolean onCreate() {
        mHelper = new Schema.OpenHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final SQLiteDatabase db = mHelper.getReadableDatabase();

        switch (new ProviderMap(uri).getResourceType()) {
        case ProviderMap.FOLDERS:
            return db.rawQuery(String.format(FOLDER_QUERY, selection, sortOrder), selectionArgs);
        case ProviderMap.FOLDERS_ID:
            return db.rawQuery(String.format(FOLDER_QUERY, ID_FILTER, NO_ORDER), new String[] { String.valueOf(ContentUris.parseId(uri)) });
        default:
            return null;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final int resourceType = new ProviderMap(uri).getResourceType();

        if (resourceType == ProviderMap.FOLDERS) {
            final SQLiteStatement stmt = db.compileStatement(FOLDER_INSERT_QUERY);
            stmt.bindString(1, (String)values.get("name"));
            stmt.bindString(2, (String)values.get("private"));
            stmt.bindString(3, (String)values.get("archived"));
            stmt.bindString(4, (String)values.get("ord"));
            try {
                return ContentUris.withAppendedId(uri, stmt.executeInsert());
            } finally {
                stmt.close();
            }
        } else if (resourceType == ProviderMap.FOLDERS_ID) {
            final SQLiteStatement stmt = db.compileStatement(FOLDER_REPLACE_QUERY);
            stmt.bindString(1, (String)values.get("id"));
            stmt.bindString(2, (String)values.get("name"));
            stmt.bindString(3, (String)values.get("private"));
            stmt.bindString(4, (String)values.get("archived"));
            stmt.bindString(5, (String)values.get("ord"));
            try {
                stmt.executeInsert();
                return uri;
            } finally {
                stmt.close();
            }
        } else {
            return null;
        }
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final int resourceType = new ProviderMap(uri).getResourceType();

        if (resourceType == ProviderMap.TASKS) {
            final SQLiteStatement stmt = db.compileStatement(String.format(FOLDER_UPDATE_QUERY, selection == null ? "" : String.format("WHERE %s", selection)));
            stmt.bindString(1, (String)values.get("name"));
            stmt.bindString(2, (String)values.get("private"));
            stmt.bindString(3, (String)values.get("archived"));
            stmt.bindString(4, (String)values.get("ord"));

            int offset = 5;
            for (final String arg: selectionArgs) {
                stmt.bindString(offset++, arg);
            }
            try {
                return stmt.executeUpdateDelete();
            } finally {
                stmt.close();
            }
        } else {
            return 0;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();

        switch (new ProviderMap(uri).getResourceType()) {
        case ProviderMap.TASKS:
            final SQLiteStatement stmt =
                db.compileStatement(String.format(FOLDER_DELETE_QUERY, selection == null ? "" : String.format("WHERE %s", selection)));

            int offset = 1;
            for (final String arg: selectionArgs) {
                stmt.bindString(offset++, arg);
            }
            try {
                return stmt.executeUpdateDelete();
            } finally {
                stmt.close();
            }
        default:
            return 0;
        }
    }
}
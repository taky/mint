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
        "_id", "folder", "name", "private", "archived", "ord"
    };

    public static final String DEFAULT_ORDER = "order by name";
    public static final String NO_ORDER = "";
    public static final String ID_FILTER = "_id=?";
    public static final String ALL_FILTER = "1=1";
    public static final String MULTIPLE_FOLDERS_FILTER = "folder in (%s)";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_FOLDER = "folder";
    public static final String COLUMN_NAME = "name";
    public static final String COLUMN_PRIVATE = "private";
    public static final String COLUMN_ARCHIVED = "archived";
    public static final String COLUMN_ORD = "ord";

    public static final int COL_ID = 0;
    public static final int COL_NAME = 1;
    public static final int COL_PRIVATE = 2;
    public static final int COL_ARCHIVED = 3;
    public static final int COL_ORD = 4;

    private static final String FOLDER_QUERY = "SELECT _id,folder,name,private,archived,ord FROM folders WHERE %s %s";

    private static final String FOLDER_INSERT_QUERY = "INSERT OR IGNORE INTO folders (folder,name,private,archived,ord) VALUES (?,?,?,?,?)";

    private static final String FOLDER_REPLACE_QUERY = "REPLACE INTO folders (_id,folder,name,private,archived,ord) VALUES (?,?,?,?,?,?)";

    private static final String FOLDER_UPDATE_QUERY = "UPDATE folders set folder=?,name=?,private=?,archived=?,ord=? %s";

    private static final String FOLDER_DELETE_QUERY = "DELETE FROM folders %s";

    private SQLiteOpenHelper mHelper;

    @Override
    public String getType(Uri uri) {
        return new ProviderMap(uri).getContentType();
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        int affected = 0;
        for (final ContentValues value : values) {
            if (insert(uri, value) != null) {
                ++affected;
            }
        }
        return affected;
    }

    @Override
    public boolean onCreate() {
        mHelper = new Schema.OpenHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final SQLiteDatabase db = mHelper.getReadableDatabase();
        Cursor c = null;

        switch (new ProviderMap(uri).getResourceType()) {
        case ProviderMap.FOLDERS:
            c = db.rawQuery(String.format(FOLDER_QUERY, selection == null ? ALL_FILTER : selection, sortOrder == null ? DEFAULT_ORDER : sortOrder), selectionArgs);
            break;
        case ProviderMap.FOLDERS_ID:
            c = db.rawQuery(String.format(FOLDER_QUERY, ID_FILTER, NO_ORDER), new String[] { String.valueOf(ContentUris.parseId(uri)) });
            break;
        }

        if (c != null) {
            c.setNotificationUri(getContext().getContentResolver(), uri);
        }

        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final SQLiteDatabase db = mHelper.getWritableDatabase();
        final int resourceType = new ProviderMap(uri).getResourceType();

        if (resourceType == ProviderMap.FOLDERS) {
            final SQLiteStatement stmt = db.compileStatement(FOLDER_INSERT_QUERY);
            ProviderUtils.bind(stmt, values, new String[] {
                    "folder", "name", "private", "archived", "ord"
            });
            try {
                final long id = stmt.executeInsert();
                if (id >= 0) {
                    return ContentUris.withAppendedId(uri, id);
                } else {
                    return null;
                }
            } finally {
                stmt.close();
            }
        } else if (resourceType == ProviderMap.FOLDERS_ID) {
            final SQLiteStatement stmt = db.compileStatement(FOLDER_REPLACE_QUERY);
            ProviderUtils.bind(stmt, values, new String[] {
                    "_id", "folder", "name", "private", "archived", "ord"
            });
            try {
                if (stmt.executeInsert() >= 0) {
                    return uri;
                } else {
                    return null;
                }
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

        if (resourceType == ProviderMap.FOLDERS) {
            if (MULTIPLE_FOLDERS_FILTER.equals(selection)) {
                selection = ProviderUtils.expandFilter(selection, selectionArgs);
            }

            final SQLiteStatement stmt = db.compileStatement(String.format(FOLDER_UPDATE_QUERY, selection == null ? "" : String.format("WHERE %s", selection)));
            int offset = ProviderUtils.bind(stmt, values, new String[] {
                    "folder", "name", "private", "archived", "ord"
            });

            for (final String arg: selectionArgs) {
                if (arg != null) {
                    stmt.bindString(offset++, arg);
                } else {
                    stmt.bindNull(offset++);
                }
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
        case ProviderMap.FOLDERS:
            if (MULTIPLE_FOLDERS_FILTER.equals(selection)) {
                selection = ProviderUtils.expandFilter(selection, selectionArgs);
            }

            final SQLiteStatement stmt =
                db.compileStatement(String.format(FOLDER_DELETE_QUERY, selection == null ? "" : String.format("WHERE %s", selection)));

            int offset = 1;
            for (final String arg: selectionArgs) {
                if (arg != null) {
                    stmt.bindString(offset++, arg);
                } else {
                    stmt.bindNull(offset++);
                }
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

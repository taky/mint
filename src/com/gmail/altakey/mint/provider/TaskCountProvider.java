package com.gmail.altakey.mint.provider;

import android.content.Context;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.net.Uri;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.database.sqlite.SQLiteStatement;
import android.content.UriMatcher;
import android.content.ContentUris;
import android.database.Cursor;
import android.database.MatrixCursor;

import android.util.Log;

import java.util.Arrays;
import java.util.List;

import com.gmail.altakey.mint.util.ProviderUtils;

public class TaskCountProvider extends BaseProvider {
    public static final Uri CONTENT_URI_TOP = Uri.parse(String.format("content://%s/count/top", ProviderMap.AUTHORITY_TASK_COUNT));
    public static final Uri CONTENT_URI_BY_STATUS = Uri.parse(String.format("content://%s/count/by-status", ProviderMap.AUTHORITY_TASK_COUNT));
    public static final Uri CONTENT_URI_BY_FOLDER = Uri.parse(String.format("content://%s/count/by-folder", ProviderMap.AUTHORITY_TASK_COUNT));
    public static final Uri CONTENT_URI_BY_CONTEXT = Uri.parse(String.format("content://%s/count/by-context", ProviderMap.AUTHORITY_TASK_COUNT));

    public static final String[] PROJECTION = new String[] {
        "_id", "title", "count", "type"
    };

    public static final String DEFAULT_ORDER = "";
    public static final String ALL_FILTER = "1=1";
    public static final String FOLDER_DEFAULT_ORDER = "folders.ord";
    private static final String FOLDER_ACTIVE_FILTER = "folders.archived=0";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TITLE = "title";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_TYPE = "type";

    public static final int COL_ID = 0;
    public static final int COL_COOKIE = 1;
    public static final int COL_COUNT = 2;
    public static final int COL_TYPE = 3;

    public static final int TYPE_SECTION = 0;
    public static final int TYPE_STATUS = 1;
    public static final int TYPE_FOLDER = 2;
    public static final int TYPE_CONTEXT = 3;

    private static final String QUERY_BY_STATUS = String.format("SELECT statuses.status AS _id,statuses.name AS title,(SELECT COUNT(1) FROM tasks WHERE tasks.status=status) AS count, %d as type FROM statuses WHERE %%s", TYPE_STATUS);
    private static final String QUERY_BY_FOLDER = String.format("SELECT folders.folder AS _id,folders.name AS title,(SELECT COUNT(1) FROM tasks WHERE tasks.folder=folder) AS count, 2 as type FROM folders WHERE %%s ORDER BY %%s", TYPE_FOLDER);
    private static final String QUERY_BY_CONTEXT = String.format("SELECT contexts.context AS _id,contexts.name AS title,(SELECT COUNT(1) FROM tasks WHERE tasks.context=context) AS count, 3 as type FROM contexts WHERE %%s", TYPE_CONTEXT);

    @Override
    public Cursor doQuery(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        final SQLiteDatabase db = mHelper.getReadableDatabase();
        final SectionHeaderBuilder headerBuilder = new SectionHeaderBuilder();

        switch (new ProviderMap(uri).getResourceType()) {
        case ProviderMap.TASK_COUNT_TOP:
            return new MergeCursor(
                new Cursor[] {
                    headerBuilder.build("status"),
                    db.rawQuery(String.format(QUERY_BY_STATUS, ALL_FILTER, DEFAULT_ORDER), null),
                    headerBuilder.build("context"),
                    db.rawQuery(String.format(QUERY_BY_CONTEXT, ALL_FILTER, DEFAULT_ORDER), null),
                    headerBuilder.build("folder"),
                    db.rawQuery(String.format(QUERY_BY_FOLDER, FOLDER_ACTIVE_FILTER, FOLDER_DEFAULT_ORDER), null),
                }
            );
        case ProviderMap.TASK_COUNT_BY_STATUS:
            return db.rawQuery(String.format(QUERY_BY_STATUS, selection == null ? ALL_FILTER : selection, sortOrder == null ? DEFAULT_ORDER : sortOrder), selectionArgs);
        case ProviderMap.TASK_COUNT_BY_FOLDER:
            return db.rawQuery(String.format(QUERY_BY_FOLDER, selection == null ? ALL_FILTER : selection, sortOrder == null ? FOLDER_DEFAULT_ORDER : sortOrder), selectionArgs);
        case ProviderMap.TASK_COUNT_BY_CONTEXT:
            return db.rawQuery(String.format(QUERY_BY_CONTEXT, selection == null ? ALL_FILTER : selection, sortOrder == null ? DEFAULT_ORDER : sortOrder), selectionArgs);
        default:
            return null;
        }
    }


    private static class SectionHeaderBuilder {
        private int mNextId = 2147483600;

        private Cursor build(String title) {
            final MatrixCursor c = new MatrixCursor(new String[] { COLUMN_ID, COLUMN_TITLE, COLUMN_COUNT, COLUMN_TYPE } );
            c.addRow(new Object[] { ++mNextId, title, 0, TYPE_SECTION } );
            return c;
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException();
    }
}

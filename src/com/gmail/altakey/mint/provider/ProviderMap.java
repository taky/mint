package com.gmail.altakey.mint.provider;

import android.content.UriMatcher;
import android.net.Uri;

public class ProviderMap {
    public static final String AUTHORITY_TASK = TaskProvider.class.getCanonicalName();
    public static final String AUTHORITY_FOLDER = TaskFolderProvider.class.getCanonicalName();
    public static final String AUTHORITY_CONTEXT = TaskContextProvider.class.getCanonicalName();
    public static final String AUTHORITY_TASK_COUNT = TaskCountProvider.class.getCanonicalName();

    private static UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    public static final int TASKS = 1;
    public static final int TASKS_ID = 2;
    public static final int FOLDERS = 3;
    public static final int FOLDERS_ID = 4;
    public static final int CONTEXTS = 5;
    public static final int CONTEXTS_ID = 6;
    public static final int STATUSES = 7;
    public static final int STATUSES_ID = 8;
    public static final int TASK_COUNT_BY_STATUS = 9;
    public static final int TASK_COUNT_BY_FOLDER = 10;
    public static final int TASK_COUNT_BY_CONTEXT = 11;
    public static final int TASK_COUNT_TOP = 12;

    private static final String SINGLE_ITEM_TYPE = "vnd.android.cursor.item/%s";
    private static final String MULTIPLE_ITEM_TYPE = "vnd.android.cursor.dir/%s";

    static {
        sMatcher.addURI(AUTHORITY_TASK, "tasks", TASKS);
        sMatcher.addURI(AUTHORITY_TASK, "tasks/#", TASKS_ID);
        sMatcher.addURI(AUTHORITY_FOLDER, "folders", FOLDERS);
        sMatcher.addURI(AUTHORITY_FOLDER, "folders/#", FOLDERS_ID);
        sMatcher.addURI(AUTHORITY_CONTEXT, "contexts", CONTEXTS);
        sMatcher.addURI(AUTHORITY_CONTEXT, "contexts/#", CONTEXTS_ID);
        sMatcher.addURI(AUTHORITY_TASK, "statuses", STATUSES);
        sMatcher.addURI(AUTHORITY_TASK, "statuses/#", STATUSES_ID);
        sMatcher.addURI(AUTHORITY_TASK_COUNT, "count/top", TASK_COUNT_TOP);
        sMatcher.addURI(AUTHORITY_TASK_COUNT, "count/by-status", TASK_COUNT_BY_STATUS);
        sMatcher.addURI(AUTHORITY_TASK_COUNT, "count/by-folder", TASK_COUNT_BY_FOLDER);
        sMatcher.addURI(AUTHORITY_TASK_COUNT, "count/by-context", TASK_COUNT_BY_CONTEXT);
    }

    private final Uri mUri;

    public ProviderMap(final Uri uri) {
        mUri = uri;
    }

    public int getResourceType() {
        return sMatcher.match(mUri);
    }

    public String getContentType() {
        switch (getResourceType()) {
        case TASKS:
            return String.format(MULTIPLE_ITEM_TYPE, "task");
        case TASKS_ID:
            return String.format(SINGLE_ITEM_TYPE, "task");
        case FOLDERS:
            return String.format(MULTIPLE_ITEM_TYPE, "folder");
        case FOLDERS_ID:
            return String.format(SINGLE_ITEM_TYPE, "folder");
        case CONTEXTS:
            return String.format(MULTIPLE_ITEM_TYPE, "context");
        case CONTEXTS_ID:
            return String.format(SINGLE_ITEM_TYPE, "context");
        case STATUSES:
            return String.format(MULTIPLE_ITEM_TYPE, "status");
        case STATUSES_ID:
            return String.format(SINGLE_ITEM_TYPE, "status");
        case TASK_COUNT_TOP:
            return String.format(SINGLE_ITEM_TYPE, "count-top");
        case TASK_COUNT_BY_STATUS:
            return String.format(SINGLE_ITEM_TYPE, "count-by-status");
        case TASK_COUNT_BY_FOLDER:
            return String.format(SINGLE_ITEM_TYPE, "count-by-folder");
        case TASK_COUNT_BY_CONTEXT:
            return String.format(SINGLE_ITEM_TYPE, "count-by-context");
        default:
            return null;
        }
    }
}

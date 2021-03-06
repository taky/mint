package com.gmail.altakey.mint.service;

import android.app.Service;
import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContentValues;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;

import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.Date;
import java.io.IOException;
import java.util.Set;
import java.util.HashSet;

import com.gmail.altakey.mint.util.Authenticator;
import com.gmail.altakey.mint.provider.TaskProvider;
import com.gmail.altakey.mint.provider.TaskContextProvider;
import com.gmail.altakey.mint.provider.TaskFolderProvider;
import com.gmail.altakey.mint.provider.TaskCountProvider;
import com.gmail.altakey.mint.model.Task;
import com.gmail.altakey.mint.model.TaskContext;
import com.gmail.altakey.mint.model.TaskFolder;
import com.gmail.altakey.mint.model.TaskStatus;

public class ToodledoClientService extends IntentService {
    public static final String ACTION_ADD = "com.gmail.altakey.mint.ADD";
    public static final String ACTION_DELETE = "com.gmail.altakey.mint.DELETE";
    public static final String ACTION_UPDATE = "com.gmail.altakey.mint.UPDATE";
    public static final String ACTION_COMPLETE = "com.gmail.altakey.mint.COMPLETE";
    public static final String ACTION_SYNC = "com.gmail.altakey.mint.SYNC";
    public static final String ACTION_SYNC_DONE = "com.gmail.altakey.mint.SYNC_DONE";
    public static final String ACTION_SYNC_BEGIN = "com.gmail.altakey.mint.SYNC_BEGIN";
    public static final String ACTION_SYNC_ABORT = "com.gmail.altakey.mint.SYNC_ABORT";
    public static final String ACTION_LOGIN_REQUIRED = "com.gmail.altakey.mint.LOGIN_REQUIRED";

    public static final String EXTRA_TASK = "task";
    public static final String EXTRA_TASKS = "tasks";
    public static final String EXTRA_TASK_FIELDS = "task_fields";
    public static final String EXTRA_ABORT_REASON = "reason";

    public static final String KEY_COMPLETE_QUEUE = "complete_queue";

    private ToodledoClient mClient;

    public ToodledoClientService() {
        super("ToodledoClientService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mClient = new ToodledoClient(Authenticator.create(this), this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    public static String asTask(Task task) {
        return getGson().toJson(task, Task.class);
    }

    public static String asListOfTasks(Task... tasks) {
        return getGson().toJson(tasks, Task[].class);
    }

    public static String asListOfTasks(List<Task> tasks) {
        return getGson().toJson(tasks.toArray(new Task[]{}), Task[].class);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        final String action = intent.getAction();

        try {
            if (ACTION_ADD.equals(action)) {
                add(extractTasks(intent));
                sync_done();
            }
            if (ACTION_DELETE.equals(action)) {
                delete(extractTasks(intent));
                sync_done();
            }
            if (ACTION_UPDATE.equals(action)) {
                update(extractTasks(intent));
                sync_done();
            }
            if (ACTION_COMPLETE.equals(action)) {
                final List<Task> targets = extractCompleteQueue();
                complete(targets);
                update(targets);
                sync_done();
            }
            if (ACTION_SYNC.equals(action)) {
                sync();
                sync_done();
            }
        } catch (IOException e) {
            abort(e.getMessage());
        } catch (Authenticator.FailureException e) {
            fail();
        } catch (Authenticator.BogusException e) {
            require();
        } catch (Authenticator.Exception e) {
            abort(e.getMessage());
        }
    }

    private List<Task> extractTasks(final Intent intent) {
        final String json = intent.getStringExtra(EXTRA_TASK);
        return getGson().fromJson(json, new TypeToken<LinkedList<Task>>(){}.getType());
    }

    private void abort(String message) {
        final Intent intent = new Intent(ACTION_SYNC_ABORT);
        intent.putExtra(EXTRA_ABORT_REASON, message);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void begin() {
        final Intent intent = new Intent(ACTION_SYNC_BEGIN);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void fail() {
        abort("login failure");
    }

    private void require() {
        final Intent intent = new Intent(ACTION_LOGIN_REQUIRED);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void add(final List<Task> tasks) throws IOException, Authenticator.Exception {
        Log.d("TCS", "adding task");
        for (Task t : mClient.addTasks(tasks, null)) {
            final ContentValues row = new ContentValues();
            row.put(TaskProvider.COLUMN_TASK, t.id);
            getContentResolver().update(TaskProvider.CONTENT_URI, row, TaskProvider.COOKIE_FILTER, new String[] { t.getContentKey() });
        }
    }

    private void delete(final List<Task> tasks) throws IOException, Authenticator.Exception {
        Log.d("TCS", "removing task");
        mClient.deleteTasks(tasks);
    }

    private void update(final List<Task> tasks) throws IOException, Authenticator.Exception {
        Log.d("TCS", "updating task");
        mClient.editTasks(tasks, null);
    }

    private List<Task> extractCompleteQueue() {
        final SharedPreferences sp = getSharedPreferences("com.gmail.altakey.mint", Context.MODE_PRIVATE);
        final Set<String> queue = sp.getStringSet(KEY_COMPLETE_QUEUE, null);
        if (queue == null) {
            return null;
        } else {
            final List<Task> ret = new LinkedList();
            for (String key: queue) {
                final Cursor c = getContentResolver().query(TaskProvider.CONTENT_URI, TaskProvider.PROJECTION, TaskProvider.COOKIE_FILTER, new String[] { key }, TaskProvider.NO_ORDER);
                for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()) {
                    ret.add(Task.fromCursor(c, 0));
                }
            }
            return ret;
        }
    }

    private void complete(final List<Task> tasks) throws IOException, Authenticator.Exception {
        final SharedPreferences sp = getSharedPreferences("com.gmail.altakey.mint", Context.MODE_PRIVATE);
        final Set<String> queue = sp.getStringSet(KEY_COMPLETE_QUEUE, null);
        Log.d("TCS", "completing tasks");
        final ContentResolver resolver = getContentResolver();
        for (Task t : tasks) {
            t.markAsDone();
            final String contentKey = t.getContentKey();
            final ContentValues values = new ContentValues();
            values.put(TaskProvider.COLUMN_COMPLETED, t.completed);
            final int affected = resolver.update(TaskProvider.CONTENT_URI, values, TaskProvider.COOKIE_FILTER, new String[] { contentKey });
            if (queue != null) {
                queue.remove(contentKey);
            }
        }
        if (queue != null) {
            sp.edit().putStringSet(KEY_COMPLETE_QUEUE, queue).commit();
        }
        resolver.notifyChange(TaskProvider.CONTENT_URI, null);
    }

    private void sync() throws IOException, Authenticator.Exception {
        final Synchronizer sync = new Synchronizer(this, mClient);
        sync.update();
    }

    private void sync_done() {
        final Intent intent = new Intent(ACTION_SYNC_DONE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private static Gson getGson() {
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(TaskStatus.class, new TaskStatus.JsonAdapter());
        builder.registerTypeAdapter(TaskFolder.class, new TaskFolder.JsonAdapter());
        builder.registerTypeAdapter(Task.class, new Task.JsonAdapter());
        builder.registerTypeAdapter(TaskContext.class, new TaskContext.JsonAdapter());
        return builder.create();
    }

    public static class Synchronizer {
        private Context mmContext;
        private ToodledoClient mmClient;

        public Synchronizer(final Context context, final ToodledoClient client) {
            mmContext = context;
            mmClient = client;
        }

        private Map<String, Long> updatedSince(TaskStatus s) {
            final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mmContext);
            final Map<String, Long> out = new HashMap<String, Long>();

            final TaskStatus known = new TaskStatus();
            known.lastedit_task = pref.getLong("lastedit_task", 0);
            known.lastdelete_task = pref.getLong("lastdelete_task", 0);
            known.lastedit_context = pref.getLong("lastedit_context", 0);
            known.lastedit_folder = pref.getLong("lastedit_folder", 0);

            if (s.lastedit_folder > known.lastedit_folder) {
                out.put("folder", 1L);
            }
            if (s.lastedit_context > known.lastedit_context) {
                out.put("context", 1L);
            }
            if (s.lastedit_task > known.lastedit_task) {
                out.put("task", known.lastedit_task);
            }
            if (s.lastdelete_task > known.lastdelete_task) {
                out.put("task_delete", known.lastdelete_task);
            }
            return out;
        }

        public void update() throws IOException, Authenticator.BogusException, Authenticator.FailureException, Authenticator.ErrorException {
            final TaskStatus st = mmClient.getStatus();
            final Map<String, Long> flags = updatedSince(st);
            final Map<String, List<?>> data = new HashMap<String, List<?>>();
            final Set<String> notifyNeeded = new HashSet<String>();

            if (flags.containsKey("folder")) {
                data.put("folder", mmClient.getFolders());
            }

            if (flags.containsKey("context")) {
                data.put("context", mmClient.getContexts());
            }

            if (flags.containsKey("task_delete")) {
                data.put("task_delete", mmClient.getTasksDeletedAfter(flags.get("task_delete")));
            }

            if (flags.containsKey("task")) {
                data.put("task", mmClient.getTasksAfter(flags.get("task")));
            }

            final ContentResolver resolver = mmContext.getContentResolver();
            if (data.containsKey("folder")) {
                final List<ContentValues> rows = new LinkedList<ContentValues>();
                for (TaskFolder t : (List<TaskFolder>)data.get("folder")) {
                    final ContentValues row = new ContentValues();
                    row.put(TaskFolderProvider.COLUMN_FOLDER, t.id);
                    row.put(TaskFolderProvider.COLUMN_NAME, t.name);
                    row.put(TaskFolderProvider.COLUMN_PRIVATE, t.private_);
                    row.put(TaskFolderProvider.COLUMN_ARCHIVED, t.archived);
                    row.put(TaskFolderProvider.COLUMN_ORD, t.ord);
                    rows.add(row);
                }
                resolver.delete(TaskFolderProvider.CONTENT_URI, null, null);
                resolver.bulkInsert(TaskFolderProvider.CONTENT_URI, rows.toArray(new ContentValues[] {}));
                notifyNeeded.add("folder");
            }

            if (data.containsKey("context")) {
                final List<ContentValues> rows = new LinkedList<ContentValues>();
                for (TaskContext t : (List<TaskContext>)data.get("context")) {
                    final ContentValues row = new ContentValues();
                    row.put(TaskContextProvider.COLUMN_CONTEXT, t.id);
                    row.put(TaskContextProvider.COLUMN_NAME, t.name);
                    rows.add(row);
                }
                resolver.delete(TaskContextProvider.CONTENT_URI, null, null);
                resolver.bulkInsert(TaskContextProvider.CONTENT_URI, rows.toArray(new ContentValues[] {}));
                notifyNeeded.add("context");
            }

            if (data.containsKey("task_delete")) {
                final List<String> args = new LinkedList<String>();
                for (Task t : (List<Task>)data.get("task_delete")) {
                    args.add(String.valueOf(t.id));
                }
                if (0 < resolver.delete(TaskProvider.CONTENT_URI, TaskProvider.MULTIPLE_TASKS_FILTER, args.toArray(new String[] {}))) {
                    notifyNeeded.add("task");
                }
            }

            if (data.containsKey("task")) {
                final List<ContentValues> rows = new LinkedList<ContentValues>();
                for (Task t : (List<Task>)data.get("task")) {
                    final ContentValues row = new ContentValues();
                    row.put(TaskProvider.COLUMN_TASK, t.id);
                    row.put(TaskProvider.COLUMN_COOKIE, t.getContentKey());
                    row.put(TaskProvider.COLUMN_TITLE, t.title);
                    row.put(TaskProvider.COLUMN_NOTE, t.note);
                    row.put(TaskProvider.COLUMN_MODIFIED, t.modified);
                    row.put(TaskProvider.COLUMN_COMPLETED, t.completed);
                    row.put(TaskProvider.COLUMN_FOLDER, t.folder);
                    row.put(TaskProvider.COLUMN_CONTEXT, t.context);
                    row.put(TaskProvider.COLUMN_PRIORITY, t.priority);
                    row.put(TaskProvider.COLUMN_STAR, t.star);
                    row.put(TaskProvider.COLUMN_DUEDATE, t.duedate);
                    row.put(TaskProvider.COLUMN_DUETIME, t.duetime);
                    row.put(TaskProvider.COLUMN_STATUS, t.status);
                    rows.add(row);
                }
                if (0 < resolver.bulkInsert(TaskProvider.CONTENT_URI, rows.toArray(new ContentValues[] {}))) {
                    notifyNeeded.add("task");
                }
            }

            recordStatus(st);

            for (final String key: notifyNeeded) {
                if ("task".equals(key)) {
                    resolver.notifyChange(TaskProvider.CONTENT_URI, null);
                    resolver.notifyChange(TaskCountProvider.CONTENT_URI_BY_FOLDER, null);
                    resolver.notifyChange(TaskCountProvider.CONTENT_URI_BY_CONTEXT, null);
                    resolver.notifyChange(TaskCountProvider.CONTENT_URI_BY_STATUS, null);
                }
                if ("folder".equals(key)) {
                    resolver.notifyChange(TaskFolderProvider.CONTENT_URI, null);
                    resolver.notifyChange(TaskCountProvider.CONTENT_URI_BY_FOLDER, null);
                }
                if ("context".equals(key)) {
                    resolver.notifyChange(TaskContextProvider.CONTENT_URI, null);
                    resolver.notifyChange(TaskCountProvider.CONTENT_URI_BY_CONTEXT, null);
                }
            }
        }

        private void recordStatus() throws IOException, Authenticator.BogusException, Authenticator.FailureException, Authenticator.ErrorException {
            recordStatus(mmClient.getStatus());
        }

        private void recordStatus(TaskStatus st) throws IOException, Authenticator.BogusException, Authenticator.FailureException, Authenticator.ErrorException {
            final SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(mmContext);
            pref.edit()
                .putLong("lastedit_folder", st.lastedit_folder)
                .putLong("lastedit_context", st.lastedit_context)
                .putLong("lastedit_goal", st.lastedit_goal)
                .putLong("lastedit_location", st.lastedit_location)
                .putLong("lastedit_task", st.lastedit_task)
                .putLong("lastdelete_task", st.lastdelete_task)
                .putLong("lastedit_notebook", st.lastedit_notebook)
                .putLong("lastdelete_notebook", st.lastdelete_notebook)
                .commit();
        }
    }
}

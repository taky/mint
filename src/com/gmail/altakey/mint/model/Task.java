package com.gmail.altakey.mint.model;

import android.database.Cursor;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Date;
import java.util.UUID;

import android.util.Log;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.apache.commons.codec.binary.Hex;

public class Task {
    public static final int COLUMNS = 11;

    public long _id;
    public String _cookie;
    public long id;
    public String title;
    public String note;
    public long modified;
    public long completed;
    public long folder;
    public long context;
    public long priority;
    public long star;
    public long duedate;
    public long duetime;
    public String status;
    public Resolved resolved = new Resolved();
    public boolean grayedout;

    public class Resolved {
        public TaskFolder folder;
        public TaskContext context;
    };

    public void markAsDone() {
        markAsDone(new Date().getTime());
    }

    public void markAsDone(long at) {
        completed = at / 1000;
    }

    public String getContentKey() {
        try {
            final MessageDigest md = MessageDigest.getInstance("MD5");
            final String base = String.format("%s.%s.%s.%s", String.valueOf(context), String.valueOf(folder), String.valueOf(status == null ? 0 : status), title);
            final byte[] bytes = base.getBytes();
            md.update(bytes, 0, bytes.length);
            final String ret =  Hex.encodeHexString(md.digest());
            Log.d("Task", String.format("content key: %s (<- %s)", ret, base));
            return ret;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static Task fromCursor(Cursor c, int offset) {
        final Task task = new Task();
        task._id = c.getLong(offset++);
        task._cookie = c.getString(offset++);
        task.id = c.getLong(offset++);
        task.title = c.getString(offset++);
        task.note = c.getString(offset++);
        task.modified = c.getLong(offset++);
        task.completed = c.getLong(offset++);
        task.priority = c.getLong(offset++);
        task.star = c.getLong(offset++);
        task.duedate = c.getLong(offset++);
        task.duetime = c.getLong(offset++);
        task.status = c.getString(offset++);
        if (c.getColumnCount() > offset) {
            task.resolved.folder = TaskFolder.fromCursor(c, offset);
            offset += TaskFolder.COLUMNS;
            if (c.getColumnCount() > offset) {
                task.resolved.context = TaskContext.fromCursor(c, offset);
            }
        }
        return task;
    }

    public static class JsonAdapter extends TypeAdapter<Task> {
        @Override
        public Task read(JsonReader reader) throws IOException {
            final Task task = new Task();
            reader.beginObject();
            while (reader.hasNext()) {
                final String name = reader.nextName();
                final String value = reader.nextString();
                if ("id".equals(name)) {
                    task.id = Long.valueOf(value);
                } else if ("title".equals(name)) {
                    task.title = value;
                } else if ("note".equals(name)) {
                    task.note = value;
                } else if ("modified".equals(name)) {
                    task.modified = Long.valueOf(value);
                } else if ("completed".equals(name)) {
                    task.completed = Long.valueOf(value);
                } else if ("folder".equals(name)) {
                    task.folder = Long.valueOf(value);
                } else if ("context".equals(name)) {
                    task.context = Long.valueOf(value);
                } else if ("priority".equals(name)) {
                    task.priority = Long.valueOf(value);
                } else if ("star".equals(name)) {
                    task.star = Long.valueOf(value);
                } else if ("duedate".equals(name)) {
                    task.duedate = Long.valueOf(value);
                } else if ("duetime".equals(name)) {
                    task.duetime = Long.valueOf(value);
                } else if ("status".equals(name)) {
                    task.status = value;
                }
            }
            reader.endObject();
            return task;
        }

        @Override
        public void write(JsonWriter writer, Task value) throws IOException {
            final Task task = value;
            writer
                .beginObject()
                .name("id").value(task.id)
                .name("title").value(task.title)
                .name("note").value(task.note)
                .name("modified").value(task.modified)
                .name("completed").value(task.completed)
                .name("folder").value(task.folder)
                .name("context").value(task.context)
                .name("priority").value(task.priority)
                .name("star").value(task.star)
                .name("duedate").value(task.duedate)
                .name("duetime").value(task.duetime)
                .name("status").value(task.status)
                .endObject();
        }
    }
}

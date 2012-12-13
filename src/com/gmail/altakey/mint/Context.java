package com.gmail.altakey.mint;

import android.database.Cursor;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class Context {
    public long id;
    public String name;

    public static Context fromCursor(Cursor c, int offset) {
        Context context = new Context();
        context.id = c.getLong(0 + offset);
        context.name = c.getString(1 + offset);
        return context.id != 0 ? context : null;
    }

    public static class JsonAdapter extends TypeAdapter<Context> {
        @Override
        public Context read(JsonReader reader) throws IOException {
            final Context context = new Context();
            reader.beginObject();
            while (reader.hasNext()) {
                final String name = reader.nextName();
                final String value = reader.nextString();
                if ("id".equals(name)) {
                    context.id = Long.valueOf(value);
                } else if ("name".equals(name)) {
                    context.name = value;
                }
            }
            reader.endObject();
            return context;
        }

        @Override
        public void write(JsonWriter writer, Context value) throws IOException {
            final Context context = value;
            writer
                .beginObject()
                .name("id").value(String.valueOf(context.id))
                .name("name").value(context.name)
                .endObject();
        }
    }
}

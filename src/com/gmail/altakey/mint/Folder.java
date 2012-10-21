package com.gmail.altakey.mint;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class Folder {
    public String id;
    public String name;
    public String private_;
    public String archived;
    public String ord;

    public class JsonAdapter extends TypeAdapter<Folder> {
        @Override
        public Folder read(JsonReader reader) throws IOException {
            final Folder folder = Folder.this;
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if ("id".equals(name)) {
                    folder.id = reader.nextString();
                } else if ("name".equals(name)) {
                    folder.name = reader.nextString();
                } else if ("private".equals(name)) {
                    folder.private_ = reader.nextString();
                } else if ("archived".equals(name)) {
                    folder.archived = reader.nextString();
                } else if ("ord".equals(name)) {
                    folder.ord = reader.nextString();
                }
            }
            reader.endObject();
            return folder;
        }

        @Override
        public void write(JsonWriter writer, Folder value) throws IOException {
            final Folder folder = Folder.this;
            writer
                .beginObject()
                .name("id").value(folder.id)
                .name("name").value(folder.name)
                .name("private").value(folder.private_)
                .name("archived").value(folder.archived)
                .name("ord").value(folder.ord)
                .endObject();
        }
    }
}

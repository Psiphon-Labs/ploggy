/*
 * Copyright (c) 2014, Psiphon Inc.
 * All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package ca.psiphon.ploggy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.NoSuchElementException;

import ca.psiphon.ploggy.Protocol.Payload;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

/**
 * Helper wrappers around GSON JSON serialization routines.
 *
 * Designed to work with the POJOs in Data and Identity, etc. Implements a custom field
 * renaming to convert Java code style "mFieldname" fieldnames to JSON style "fieldname".
 */
public class Json {

    private static final String LOG_TAG = "Json";

    private static final Gson mSerializer =
            new GsonBuilder().
                    serializeNulls().
                    setDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").
                    setFieldNamingStrategy(new CustomFieldNamingStrategy()).
                    registerTypeAdapter(Protocol.Payload.class, new PayloadDeserializer()).
                    create();

    public static String toJson(Object object) {
        return mSerializer.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> type) throws PloggyError {
        try {
            return mSerializer.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new PloggyError(LOG_TAG, e);
        }
    }

    public static class PayloadIterator implements Iterable<Protocol.Payload>, Iterator<Protocol.Payload> {

        private final JsonReader mJsonReader;

        public PayloadIterator(InputStream inputStream) throws PloggyError {
            // *TODO* double check mJsonReader.close() closes input stream
            try {
                mJsonReader = new JsonReader(new InputStreamReader(inputStream, "UTF-8"));
                // Set lenient mode to accept multiple JSON objects, one after the
                // other, in the same stream
                mJsonReader.setLenient(true);
            } catch (UnsupportedEncodingException e) {
                throw new PloggyError(LOG_TAG, e);
            }
        }

        public PayloadIterator(String input) throws PloggyError {
            this(Utils.makeInputStream(input));
        }

        @Override
        public Iterator<Protocol.Payload> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            try {
                return mJsonReader.peek() != JsonToken.END_DOCUMENT;
            } catch (IOException e) {
                return false;
            }
        }

        @Override
        public Protocol.Payload next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            try {
                Protocol.Payload payload = mSerializer.fromJson(mJsonReader, Protocol.Payload.class);
                if (!hasNext()) {
                    mJsonReader.close();
                }
                return payload;
            } catch (JsonIOException e) {
                Log.addEntry(LOG_TAG, e.getMessage());
            } catch (JsonSyntaxException e) {
                Log.addEntry(LOG_TAG, e.getMessage());
            } catch (IOException e) {
                Log.addEntry(LOG_TAG, e.getMessage());
            }
            throw new NoSuchElementException();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        public void close() {
            try {
                mJsonReader.close();
            } catch (IOException e) {
            }

        }
    }

    private static class PayloadDeserializer implements JsonDeserializer<Protocol.Payload> {
        @Override
        public Payload deserialize(
                JsonElement json,
                Type typeOfT,
                JsonDeserializationContext context) throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();
            Protocol.Payload.Type payloadType = null;
            Type objectType = null;
            // Determine JSON object type via unique field names
            if (jsonObject.has("members")) {
                payloadType = Protocol.Payload.Type.GROUP;
                objectType = Protocol.Group.class;
            } else if (jsonObject.has("content")) {
                payloadType = Protocol.Payload.Type.POST;
                objectType = Protocol.Post.class;
            } else if (jsonObject.has("latitude")) {
                payloadType = Protocol.Payload.Type.LOCATION;
                objectType = Protocol.Location.class;
            } else {
                throw new JsonParseException("unrecognized payload type");
            }
            // *TODO* confirm not getting infinite loop mentioned here:
            // http://google-gson.googlecode.com/svn/trunk/gson/docs/javadocs/com/google/gson/JsonDeserializationContext.html
            // if so, alternative? new Gson().fromJson(json, type);
            return new Protocol.Payload(payloadType, context.deserialize(json, objectType));
        }
    }

    private static class CustomFieldNamingStrategy implements FieldNamingStrategy {

        @Override
        public String translateName(Field field) {
            return Character.toLowerCase(field.getName().charAt(1)) + field.getName().substring(2);
        }
    }
}

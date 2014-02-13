/*
 * Copyright (c) 2013, Psiphon Inc.
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.util.Iterator;

import ca.psiphon.ploggy.Protocol.Payload;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

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
                    create();

    public static String toJson(Object object) {
        return mSerializer.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> type) throws Utils.ApplicationError {
        try {
            return mSerializer.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    // TODO: remove this function if not used
    /*
    public static <T> ArrayList<T> fromJsonStream(InputStream inputStream, Class<T> type) throws Utils.ApplicationError {
        // Reads succession of JSON objects from a stream. This does *not* expect a well-formed JSON array.
        // Designed to work with the log file, which is constantly appended to.
        try {
            JsonReader jsonReader = new JsonReader(new InputStreamReader(inputStream, "UTF-8"));
            jsonReader.setLenient(true);
            ArrayList<T> array = new ArrayList<T>();
            while (jsonReader.peek() != JsonToken.END_DOCUMENT) {
                array.add((T)mSerializer.fromJson(jsonReader, type));
            }
            jsonReader.close();
            return array;
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (JsonSyntaxException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }
    */

    public static class PayloadIterator implements Iterable<Protocol.Payload>, Iterator<Protocol.Payload> {

        private final InputStream mInputStream;

        public PayloadIterator(InputStream inputStream) {
            mInputStream = inputStream;
        }

        public PayloadIterator(String input) throws Utils.ApplicationError {
            try {
                mInputStream = new ByteArrayInputStream(input.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                throw new Utils.ApplicationError(LOG_TAG, e);
            }
        }

        @Override
        public Iterator<Payload> iterator() {
            return this;
        }

        @Override
        public boolean hasNext() {
            // *TODO* implement
            return false;
        }

        @Override
        public Payload next() {
            // *TODO* implement
            return null;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private static class CustomFieldNamingStrategy implements FieldNamingStrategy {

        @Override
        public String translateName(Field field) {
            return Character.toLowerCase(field.getName().charAt(1)) + field.getName().substring(2);
        }
    }
}

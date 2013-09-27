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

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;

import com.google.gson.FieldNamingStrategy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

public class Json {

    private static final Gson mSerializer =
            new GsonBuilder().setFieldNamingStrategy(new CustomFieldNamingStrategy()).create(); 
    
    public static String toJson(Object object) {
    	return mSerializer.toJson(object);
    }

    public static <T> T fromJson(String json, Class<T> type) throws Utils.ApplicationError {
        try {
            return mSerializer.fromJson(json, type);
        } catch (JsonSyntaxException e) {
            throw new Utils.ApplicationError(e);
        }
    }

    public static <T> ArrayList<T> fromJsonArray(String json, Class<T> type) throws Utils.ApplicationError {
        try {
        	Type collectionType = new TypeToken<Collection<T>>(){}.getType();
            return mSerializer.fromJson(json, collectionType);
        } catch (JsonSyntaxException e) {
            throw new Utils.ApplicationError(e);
        }
    }

    private static class CustomFieldNamingStrategy implements FieldNamingStrategy {

        @Override
        public String translateName(Field field) {
            // TODO: ... mFieldName --> fieldName
            return Character.toLowerCase(field.getName().charAt(1)) + field.getName().substring(2);
        }
        
    }
}

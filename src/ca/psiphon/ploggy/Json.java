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

import java.io.IOException;
import java.util.ArrayList;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Json {

    private static final ObjectMapper mObjectMapper = new ObjectMapper();
    
    public static String toJson(Object object) throws Utils.ApplicationError {
        try {
            return mObjectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new Utils.ApplicationError(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) throws Utils.ApplicationError {
        try {
            return mObjectMapper.readValue(json, type);
        } catch (JsonParseException e) {
            throw new Utils.ApplicationError(e);
        } catch (JsonMappingException e) {
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }

    public static <T> ArrayList<T> fromJsonArray(String json, Class<T> type) throws Utils.ApplicationError {
        try {
            return mObjectMapper.readValue(json, new TypeReference<ArrayList<T>>() {});
        } catch (JsonParseException e) {
            throw new Utils.ApplicationError(e);
        } catch (JsonMappingException e) {
            throw new Utils.ApplicationError(e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(e);
        }
    }
}

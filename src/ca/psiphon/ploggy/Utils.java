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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.content.Context;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Utils {

    public static class ApplicationError extends Exception {
        private static final long serialVersionUID = -3656367025650685613L;

        public ApplicationError() {
        }

        public ApplicationError(Exception e) {
            super(e);
        }
    }

    public static String makeId(String nickname, String transportPublicKey, String hiddenServiceHostname) {
        // TODO: ...
        return null;
    }
    
    public static String makeIdenticon(String id) {        
        // TODO: ...
        return null;
    }    

    public static String inputStreamToString(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder value = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            value.append(line);
        }
        return value.toString();
    }

    private static Context mApplicationContext;

    public static void setApplicationContext(Context context) {
        mApplicationContext = context;
    }
    
    public static Context getApplicationContext() {
        return mApplicationContext;
    }
    
    private static final ObjectMapper mObjectMapper = new ObjectMapper();
    
    public static String toJson(Object object) throws ApplicationError {
        try {
            return mObjectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new ApplicationError(e);
        }
    }

    public static <T> T fromJson(String json, Class<T> type) throws ApplicationError {
        try {
            return mObjectMapper.readValue(json, type);
        } catch (JsonParseException e) {
            throw new ApplicationError(e);
        } catch (JsonMappingException e) {
            throw new ApplicationError(e);
        } catch (IOException e) {
            throw new ApplicationError(e);
        }
    }
}

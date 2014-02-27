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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import android.util.Pair;
import ch.boye.httpclientandroidlib.HttpEntity;
import ch.boye.httpclientandroidlib.HttpResponse;
import ch.boye.httpclientandroidlib.HttpStatus;
import ch.boye.httpclientandroidlib.client.methods.HttpGet;
import ch.boye.httpclientandroidlib.client.methods.HttpPut;
import ch.boye.httpclientandroidlib.client.methods.HttpRequestBase;
import ch.boye.httpclientandroidlib.client.utils.URIBuilder;
import ch.boye.httpclientandroidlib.conn.ClientConnectionManager;
import ch.boye.httpclientandroidlib.entity.InputStreamEntity;
import ch.boye.httpclientandroidlib.impl.client.DefaultHttpClient;
import ch.boye.httpclientandroidlib.params.BasicHttpParams;
import ch.boye.httpclientandroidlib.params.HttpConnectionParams;
import ch.boye.httpclientandroidlib.params.HttpParams;

/**
 * Client-side for Ploggy friend-to-friend requests.
 *
 * Implements HTTP requests through Tor with TLS configured with TransportSecurity specs and mutual
 * authentication.
 */
public class WebClientRequest {

    private static final String LOG_TAG = "Web Client Request";

    enum RequestType {GET, PUT};

    private final WebClientConnectionPool mWebClientConnectionPool;
    private String mHostname = null;
    private int mPort = -1;
    private RequestType mRequestType;
    private String mRequestPath = null;
    private List<Pair<String,String>> mRequestParameters = null;
    private String mRequestBodyMimeType = null;
    private long mRequestBodyLength = -1;
    private InputStream mRequestBodyInputStream = null;
    private Pair<Long, Long> mRangeHeader = null;
    private OutputStream mResponseBodyOutputStream = null;
    private ResponseBodyHandler mResponseBodyHandler = null;

    public WebClientRequest(
            WebClientConnectionPool clientConnectionPool,
            String hostname,
            int port,
            RequestType requestType,
            String requestPath) {
        mWebClientConnectionPool = clientConnectionPool;
        mHostname = hostname;
        mPort = port;
        mRequestPath = requestPath;
    }

    public WebClientRequest requestParameters(List<Pair<String,String>> requestParameters) {
        mRequestParameters = requestParameters;
        return this;
    }

    public WebClientRequest requestBody(String mimeType, long length, InputStream inputStream) {
        mRequestBodyMimeType = mimeType;
        mRequestBodyLength = length;
        mRequestBodyInputStream = inputStream;
        return this;
    }

    public WebClientRequest requestBody(String requestBody) throws Utils.ApplicationError {
        byte[] body;
        try {
            body = requestBody.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
        return requestBody("application/json", body.length, new ByteArrayInputStream(body));
    }

    public WebClientRequest rangeHeader(Pair<Long, Long> rangeHeader) {
        mRangeHeader = rangeHeader;
        return this;
    }

    public WebClientRequest responseBodyOutputStream(OutputStream responseBodyOutputStream) {
        mResponseBodyOutputStream = responseBodyOutputStream;
        mResponseBodyHandler = null;
        return this;
    }

    public interface ResponseBodyHandler {
        void consume(InputStream responseBodyInputStream) throws Utils.ApplicationError;
    }

    public WebClientRequest responseBodyHandler(ResponseBodyHandler responseBodyHandler) {
        mResponseBodyHandler = responseBodyHandler;
        mResponseBodyOutputStream = null;
        return this;
    }

    public String makeRequestAndLoadResponse() throws Utils.ApplicationError {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        responseBodyOutputStream(outputStream);
        try {
            return new String(outputStream.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        }
    }

    public void makeRequest() throws Utils.ApplicationError {
        HttpRequestBase request = null;
        ClientConnectionManager connectionManager = null;
        try {
            URIBuilder uriBuilder =
                    new URIBuilder()
                        .setScheme(Protocol.WEB_SERVER_PROTOCOL)
                        .setHost(mHostname)
                        .setPort(mPort)
                        .setPath(mRequestPath);
            if (mRequestParameters != null) {
                for (Pair<String,String> requestParameter : mRequestParameters) {
                    uriBuilder.addParameter(requestParameter.first, requestParameter.second);
                }
            }
            URI uri = uriBuilder.build();
            connectionManager = mWebClientConnectionPool.getConnectionManager();
            HttpParams params = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(params, WebClientConnectionPool.CONNECT_TIMEOUT_MILLISECONDS);
            HttpConnectionParams.setSoTimeout(params, WebClientConnectionPool.READ_TIMEOUT_MILLISECONDS);
            DefaultHttpClient client = new DefaultHttpClient(connectionManager, params);
            switch (mRequestType) {
            case GET:
                request = new HttpGet(uri);
                // TODO: assert mRequestBodyInputStream is null? it's ignored
                break;
            case PUT:
                HttpPut putRequest = new HttpPut(uri);
                if (mRequestBodyInputStream != null) {
                    InputStreamEntity entity = new InputStreamEntity(mRequestBodyInputStream, mRequestBodyLength);
                    entity.setContentType(mRequestBodyMimeType);
                    putRequest.setEntity(entity);
                }
                request = putRequest;
                break;
            }
            if (mRangeHeader != null) {
                String value = "bytes=" + Long.toString(mRangeHeader.first) + "-";
                if (mRangeHeader.second != -1) {
                    value = value + Long.toString(mRangeHeader.second);
                }
                request.addHeader("Range", value);
            }
            HttpResponse response = client.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
                throw new Utils.ApplicationError(LOG_TAG, String.format("HTTP request failed with %d", statusCode));
            }
            HttpEntity responseEntity = response.getEntity();
            if (mResponseBodyOutputStream != null) {
                Utils.copyStream(responseEntity.getContent(), mResponseBodyOutputStream);
            } else if (mResponseBodyHandler != null) {
                mResponseBodyHandler.consume(responseEntity.getContent());
            }else {
                // Even if the caller doesn't want the content, we need to consume the bytes
                // (particularly if leaving the socket up in a keep-alive state).
                Utils.discardStream(responseEntity.getContent());
            }
        } catch (URISyntaxException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (UnsupportedOperationException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IllegalStateException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IllegalArgumentException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (NullPointerException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } catch (IOException e) {
            throw new Utils.ApplicationError(LOG_TAG, e);
        } finally {
            if (request != null && !request.isAborted()) {
                request.abort();
            }
            if (connectionManager != null) {
                connectionManager.shutdown();
            }
        }
    }
}

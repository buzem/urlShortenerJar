/**
 * Copyright (c) 2016 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.urlshortener;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import site.ycsb.*;

import javax.ws.rs.HttpMethod;
import java.io.*;
import java.util.*;


public class URLShortenerClient extends DB {
  private static final String CON_TIMEOUT = "timeout.con";
  private static final String READ_TIMEOUT = "timeout.read";
  private static final String EXEC_TIMEOUT = "timeout.exec";
  private static final String LOG_ENABLED = "log.enable";
  private static final String HEADERS = "headers";
  private boolean logEnabled;

  private List<String> servers;
  private int targetServer = 0;

  private Properties props;
  private String[] headers;
  private CloseableHttpClient client;
  private int conTimeout = 10000;
  private int readTimeout = 10000;
  private int execTimeout = 10000;
  private volatile Criteria requestTimedout = new Criteria(false);

  @Override
  public void init() throws DBException {
    props = getProperties();

    String serverString = props.getProperty("servers", "http://127.0.0.1:8080/");
    System.out.println("Connecting to the following servers: " + serverString);

    servers = Arrays.asList(serverString.split(","));

    conTimeout = Integer.valueOf(props.getProperty(CON_TIMEOUT, "10")) * 1000;
    readTimeout = Integer.valueOf(props.getProperty(READ_TIMEOUT, "10")) * 1000;
    execTimeout = Integer.valueOf(props.getProperty(EXEC_TIMEOUT, "10")) * 1000;
    logEnabled = Boolean.valueOf(props.getProperty(LOG_ENABLED, "false").trim());
    headers = props.getProperty(HEADERS, "Accept */* Content-Type application/xml user-agent Mozilla/5.0 ").trim()
        .split(" ");
    setupClient();
  }

  private void setupClient() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder = requestBuilder.setConnectTimeout(conTimeout);
    requestBuilder = requestBuilder.setConnectionRequestTimeout(readTimeout);
    requestBuilder = requestBuilder.setSocketTimeout(readTimeout);
    HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(requestBuilder.build());
    this.client = clientBuilder.setConnectionManagerShared(true).build();
  }

  private String getTargetServer() {
    targetServer = (targetServer + 1) % servers.size();
    return servers.get(targetServer);
  }

  @Override
  public Status read(String table, String endpoint, Set<String> fields, Map<String, ByteIterator> result) {
    int responseCode;
    String target_server = getTargetServer();
    try {
      Map<String, ByteIterator> httpResponse = new HashMap<>();
      responseCode = httpGet(target_server + endpoint, httpResponse);
      if (responseCode == 200) {
        result.put("url", httpResponse.get("response"));
      }
    } catch (Exception e) {
      responseCode = handleExceptions(e, servers + endpoint, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("GET Request: ").append(target_server).append(endpoint).append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status insert(String table, String endpoint, Map<String, ByteIterator> values) {
    int responseCode;
    String target_server = getTargetServer();
    try {
      responseCode = httpExecute(new HttpPost(target_server), values.get("url").toString());
    } catch (Exception e) {
      System.out.println("Invalid post ");
      System.out.println(e.getMessage());
      responseCode = handleExceptions(e, target_server, HttpMethod.POST);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("POST Request: ").append(target_server).append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status delete(String table, String endpoint) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public Status update(String table, String endpoint, Map<String, ByteIterator> values) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields, Vector<HashMap<String, ByteIterator>> result) {
    return Status.NOT_IMPLEMENTED;
  }

  // Maps HTTP status codes to YCSB status codes.
  private Status getStatus(int responseCode) {
    int rc = responseCode / 100;
    if (responseCode == 400) {
      return Status.BAD_REQUEST;
    } else if (responseCode == 403) {
      return Status.FORBIDDEN;
    } else if (responseCode == 404) {
      return Status.NOT_FOUND;
    } else if (responseCode == 501) {
      return Status.NOT_IMPLEMENTED;
    } else if (responseCode == 503) {
      return Status.SERVICE_UNAVAILABLE;
    } else if (rc == 5) {
      return Status.ERROR;
    }
    return Status.OK;
  }

  private int handleExceptions(Exception e, String url, String method) {
    if (logEnabled) {
      System.err.println(new StringBuilder(method).append(" Request: ").append(url).append(" | ").append(e.getClass().getName()).append(" occured | Error message: ").append(e.getMessage()).toString());
    }

    if (e instanceof ClientProtocolException) {
      return 400;
    }
    return 500;
  }

  // Connection is automatically released back in case of an exception.
  private int httpGet(String endpoint, Map<String, ByteIterator> result) throws IOException {
    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    HttpGet request = new HttpGet(endpoint);
    for (int i = 0; i < headers.length; i = i + 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    HttpEntity responseEntity = response.getEntity();
    // If null entity don't bother about connection release.
    if (responseEntity != null) {
      InputStream stream = responseEntity.getContent();

      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      StringBuffer responseContent = new StringBuffer();
      String line = "";
      while ((line = reader.readLine()) != null) {
        if (requestTimedout.isSatisfied()) {
          // Must avoid memory leak.
          reader.close();
          stream.close();
          EntityUtils.consumeQuietly(responseEntity);
          response.close();
          client.close();
          throw new TimeoutException();
        }
        responseContent.append(line);
      }
      timer.interrupt();
      result.put("response", new StringByteIterator(responseContent.toString()));
      // Closing the input stream will trigger connection release.
      stream.close();
    }
    EntityUtils.consumeQuietly(responseEntity);
    response.close();
    client.close();
    return responseCode;
  }

  private int httpExecute(HttpEntityEnclosingRequestBase request, String data) throws IOException {
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    requestTimedout.setIsSatisfied(false);
    timer.start();
    try {
      int responseCode = 200;
      for (int i = 0; i < headers.length; i = i + 2) {
        request.setHeader(headers[i], headers[i + 1]);
      }
      InputStreamEntity reqEntity = new InputStreamEntity(new ByteArrayInputStream(data.getBytes()), ContentType.APPLICATION_FORM_URLENCODED);
      reqEntity.setChunked(true);
      request.setEntity(reqEntity);
      CloseableHttpResponse response = client.execute(request);
      responseCode = response.getStatusLine().getStatusCode();
      HttpEntity responseEntity = response.getEntity();

      // If null entity don't bother about connection release.
      if (responseEntity != null) {
        InputStream stream = responseEntity.getContent();
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        StringBuffer responseContent = new StringBuffer();
        String line = "";
        while ((line = reader.readLine()) != null) {
          if (requestTimedout.isSatisfied()) {
            // Must avoid memory leak.
            reader.close();
            stream.close();
            EntityUtils.consumeQuietly(responseEntity);
            response.close();
            client.close();
            throw new TimeoutException();
          }
          responseContent.append(line);
        }
        timer.interrupt();
        // Closing the input stream will trigger connection release.
        stream.close();
      }
      EntityUtils.consumeQuietly(responseEntity);
      response.close();
      client.close();
      return responseCode;
    } catch (Exception e) {
      if (!timer.isInterrupted()) {
        timer.interrupt();
      }
      throw e;
    }
  }

  /**
   * Marks the input {@link Criteria} as satisfied when the input time has elapsed.
   */
  class Timer implements Runnable {

    private long timeout;
    private Criteria timedout;

    public Timer(long timeout, Criteria timedout) {
      this.timedout = timedout;
      this.timeout = timeout;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(timeout);
        this.timedout.setIsSatisfied(true);
      } catch (InterruptedException e) {
        // Do nothing.
      }
    }

  }

  /**
   * Sets the flag when a criteria is fulfilled.
   */
  class Criteria {

    private boolean isSatisfied;

    public Criteria(boolean isSatisfied) {
      this.isSatisfied = isSatisfied;
    }

    public boolean isSatisfied() {
      return isSatisfied;
    }

    public void setIsSatisfied(boolean satisfied) {
      this.isSatisfied = satisfied;
    }

  }

  /**
   * Private exception class for execution timeout.
   */
  class TimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TimeoutException() {
      super("HTTP Request exceeded execution time limit.");
    }

  }
}

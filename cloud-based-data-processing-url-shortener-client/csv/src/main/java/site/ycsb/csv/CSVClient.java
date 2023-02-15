/**
 * Copyright (c) 2016 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.csv;

import java.io.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;


import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import site.ycsb.ByteIterator;
import site.ycsb.DB;
import site.ycsb.DBException;
import site.ycsb.Status;


public class CSVClient extends DB {

  private static volatile boolean initialized = false;

  private CSVPrinter printer;
  private Properties props;

  private static final String FILENAME = "./urls.csv";
  private static final String[] HEADERS ={ "alias", "expires", "url"};

  @Override
  public void init() throws DBException {
    if (initialized) {
      throw new RuntimeException("CSVClient should be used from a single thread only.");
    }
    initialized = true;

    props = getProperties();
    printer = createCSVPrinter();
  }

  public void cleanup() throws DBException {
    try {
      printer.flush();
      printer.close();
    } catch (IOException e) {
      throw new DBException("Could not close csv file.");
    }
  }

  private CSVPrinter createCSVPrinter() throws DBException {
    try {
      FileWriter out = new FileWriter(FILENAME, false);
      return new CSVPrinter(out, CSVFormat.DEFAULT);
    } catch (IOException e) {
      throw new DBException("Couldn't create CSV printer for file " + FILENAME);
    }
  }

  @Override
  public Status read(String table, String endpoint, Set<String> fields, Map<String, ByteIterator> result) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public Status insert(String table, String key, Map<String, ByteIterator> values) {
    try {
      if (!values.containsKey("url")) {
        throw new RuntimeException("No url provided.");
      }
      if (!values.containsKey("expires")) {
        throw new RuntimeException("No expiring date provided.");
      }
      String url = values.get("url").toString();
      String expires = values.get("expires").toString();

      printer.printRecord(key, expires, url);
      return Status.OK;
    } catch (IOException e) {
      return Status.ERROR;
    }
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
  public Status scan(String table, String startkey, int recordcount, Set<String> fields,
      Vector<HashMap<String, ByteIterator>> result) {
    return Status.NOT_IMPLEMENTED;
  }
}

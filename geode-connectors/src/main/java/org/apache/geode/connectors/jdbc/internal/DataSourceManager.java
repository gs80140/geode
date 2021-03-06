/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.geode.connectors.jdbc.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class DataSourceManager {

  private final JdbcDataSourceFactory jdbcDataSourceFactory;
  private final Map<String, JdbcDataSource> dataSourceMap = new ConcurrentHashMap<>();

  DataSourceManager(JdbcDataSourceFactory jdbcDataSourceFactory) {
    this.jdbcDataSourceFactory = jdbcDataSourceFactory;
  }

  JdbcDataSource getDataSource(ConnectionConfiguration config) {
    return dataSourceMap.computeIfAbsent(config.getName(), k -> {
      return this.jdbcDataSourceFactory.create(config);
    });
  }

  void close() {
    dataSourceMap.values().forEach(this::close);
  }

  private void close(JdbcDataSource dataSource) {
    try {
      dataSource.close();
    } catch (Exception e) {
      // TODO ignored for now; should it be logged?
    }
  }
}

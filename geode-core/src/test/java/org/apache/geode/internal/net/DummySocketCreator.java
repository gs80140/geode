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
package org.apache.geode.internal.net;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

import javax.net.ssl.SSLException;

import org.apache.geode.internal.admin.SSLConfig;

public class DummySocketCreator extends SocketCreator {

  private List<Integer> socketSoTimeouts;

  /**
   * Constructs new SocketCreator instance.
   */
  public DummySocketCreator(SSLConfig sslConfig, List<Integer> sockets) {
    super(sslConfig);
    this.socketSoTimeouts = sockets;
  }

  @Override
  public void configureServerSSLSocket(Socket socket) throws IOException {
    this.socketSoTimeouts.add(socket.getSoTimeout());
    throw new SSLException("This is a test SSLException");
  }

  public List<Integer> getSocketSoTimeouts() {
    return socketSoTimeouts;
  }
}

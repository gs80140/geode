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

package org.apache.geode.internal.cache;

import static org.junit.Assert.*;

import java.io.DataInput;
import java.io.DataOutputStream;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import org.apache.geode.internal.ByteArrayData;
import org.apache.geode.test.junit.categories.UnitTest;

@Category(UnitTest.class)
public class TXRemoteCommitMessageTest {

  @Test
  public void testFindRemoteTXMessageReplyDoesNotSendCommitMessage() throws Exception {
    FindRemoteTXMessage.FindRemoteTXMessageReply findRemoteTXMessageReply =
        new FindRemoteTXMessage.FindRemoteTXMessageReply();
    findRemoteTXMessageReply.isHostingTx = true;
    findRemoteTXMessageReply.txCommitMessage = new TXCommitMessage();

    ByteArrayData testStream = new ByteArrayData();
    assertTrue(testStream.isEmpty());

    DataOutputStream out = testStream.getDataOutput();
    findRemoteTXMessageReply.toData(out);
    assertTrue(testStream.size() > 0);

    DataInput in = testStream.getDataInput();
    FindRemoteTXMessage.FindRemoteTXMessageReply findRemoteTXMessageReplyFromData =
        new FindRemoteTXMessage.FindRemoteTXMessageReply();
    findRemoteTXMessageReplyFromData.fromData(in);

    assertEquals(true, findRemoteTXMessageReplyFromData.isHostingTx);
    assertNull(findRemoteTXMessageReplyFromData.txCommitMessage);
  }
}
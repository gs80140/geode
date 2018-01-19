/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to you under the Apache License, Version 2.0 (the
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
package org.apache.geode.cache.lucene;

import static org.apache.geode.cache.lucene.test.LuceneTestUtilities.*;
import static org.junit.Assert.*;

import java.util.Properties;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import org.apache.geode.cache.Cache;
import org.apache.geode.cache.PartitionAttributes;
import org.apache.geode.cache.PartitionAttributesFactory;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionShortcut;
import org.apache.geode.cache.lucene.internal.LuceneIndexFactoryImpl;
import org.apache.geode.cache.lucene.internal.LuceneQueryImpl;
import org.apache.geode.cache.lucene.internal.LuceneServiceImpl;
import org.apache.geode.cache.lucene.test.LuceneTestUtilities;
import org.apache.geode.distributed.ConfigurationProperties;
import org.apache.geode.test.dunit.AsyncInvocation;
import org.apache.geode.test.dunit.SerializableRunnableIF;
import org.apache.geode.test.dunit.VM;
import org.apache.geode.test.junit.categories.DistributedTest;

/**
 * This test class is intended to contain basic integration tests of the lucene query class that
 * should be executed against a number of different regions types and topologies.
 *
 */
@Category(DistributedTest.class)
@RunWith(JUnitParamsRunner.class)
public class LuceneQueriesDUnitTest extends LuceneQueriesAccessorBase {

  private static final long serialVersionUID = 1L;

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void transactionWithLuceneQueriesShouldThrowException(RegionTestableType regionTestType) {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));

    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));

    accessor.invoke(() -> {
      Cache cache = getCache();
      try {
        LuceneService service = LuceneServiceProvider.get(cache);
        LuceneQuery<Integer, TestObject> query;
        query = service.createLuceneQueryFactory().create(INDEX_NAME, REGION_NAME, "text:world",
            DEFAULT_FIELD);
        cache.getCacheTransactionManager().begin();
        PageableLuceneQueryResults<Integer, TestObject> results = query.findPages();
        fail();
      } catch (LuceneQueryException e) {
        if (!e.getMessage()
            .equals(LuceneQueryImpl.LUCENE_QUERY_CANNOT_BE_EXECUTED_WITHIN_A_TRANSACTION)) {
          fail();
        }
      } finally {
        cache.getCacheTransactionManager().rollback();
      }
    });
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void returnCorrectResultsFromStringQueryWithDefaultAnalyzer(
      RegionTestableType regionTestType) {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));

    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));
    executeTextSearch(accessor);
  }

  private void destroyIndex() {
    LuceneService luceneService = LuceneServiceProvider.get(getCache());
    luceneService.destroyIndex(INDEX_NAME, REGION_NAME);
  }

  private void recreateIndex() {
    LuceneService luceneService = LuceneServiceProvider.get(getCache());
    LuceneIndexFactoryImpl indexFactory =
        (LuceneIndexFactoryImpl) luceneService.createIndexFactory().addField("text");
    indexFactory.create(INDEX_NAME, REGION_NAME, true);
  };

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void dropAndRecreateIndex(RegionTestableType regionTestType) throws Exception {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));

    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));
    executeTextSearch(accessor);

    dataStore1.invoke(() -> destroyIndex());

    // re-index stored data
    AsyncInvocation ai1 = dataStore1.invokeAsync(() -> {
      recreateIndex();
    });

    // re-index stored data
    AsyncInvocation ai2 = dataStore2.invokeAsync(() -> {
      recreateIndex();
    });

    AsyncInvocation ai3 = accessor.invokeAsync(() -> {
      recreateIndex();
    });

    ai1.join();
    ai2.join();
    ai3.join();

    ai1.checkException();
    ai2.checkException();
    ai3.checkException();

    executeTextSearch(accessor);
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void reindexThenQuery(RegionTestableType regionTestType) throws Exception {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      LuceneIndexFactoryImpl indexFactory =
          (LuceneIndexFactoryImpl) luceneService.createIndexFactory().addField("text");
      indexFactory.create(INDEX_NAME, REGION_NAME, true);
    };

    // Create dataRegion prior to index
    dataStore1.invoke(() -> initDataStore(regionTestType));
    dataStore2.invoke(() -> initDataStore(regionTestType));
    accessor.invoke(() -> initAccessor(regionTestType));

    // populate region
    accessor.invoke(() -> {
      Region<Object, Object> region = getCache().getRegion(REGION_NAME);
      region.put(1, new TestObject("hello world"));
      region.put(113, new TestObject("hi world"));
      region.put(2, new TestObject("goodbye world"));
    });

    // re-index stored data
    AsyncInvocation ai1 = dataStore1.invokeAsync(() -> {
      recreateIndex();
    });

    // re-index stored data
    AsyncInvocation ai2 = dataStore2.invokeAsync(() -> {
      recreateIndex();
    });

    AsyncInvocation ai3 = accessor.invokeAsync(() -> {
      recreateIndex();
    });

    ai1.join();
    ai2.join();
    ai3.join();

    ai1.checkException();
    ai2.checkException();
    ai3.checkException();

    executeTextSearch(accessor);
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void defaultFieldShouldPropogateCorrectlyThroughFunction(
      RegionTestableType regionTestType) {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));
    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));
    executeTextSearch(accessor, "world", "text", 3);
    executeTextSearch(accessor, "world", "noEntriesMapped", 0);
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void canQueryWithCustomLuceneQueryObject(RegionTestableType regionTestType) {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));
    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));

    // Execute a query with a custom lucene query object
    accessor.invoke(() -> {
      Cache cache = getCache();
      LuceneService service = LuceneServiceProvider.get(cache);
      LuceneQuery query =
          service.createLuceneQueryFactory().create(INDEX_NAME, REGION_NAME, index -> {
            return new TermQuery(new Term("text", "world"));
          });
      final PageableLuceneQueryResults results = query.findPages();
      assertEquals(3, results.size());
    });
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void verifyWaitForFlushedFunctionOnAccessor(RegionTestableType regionTestType)
      throws InterruptedException {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));
    dataStore1.invoke(() -> LuceneTestUtilities.pauseSender(getCache()));
    dataStore2.invoke(() -> LuceneTestUtilities.pauseSender(getCache()));
    putDataInRegion(accessor);
    assertFalse(waitForFlushBeforeExecuteTextSearch(accessor, 200));
    dataStore1.invoke(() -> LuceneTestUtilities.resumeSender(getCache()));
    dataStore2.invoke(() -> LuceneTestUtilities.resumeSender(getCache()));
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    executeTextSearch(accessor, "world", "text", 3);
    executeTextSearch(accessor, "world", "noEntriesMapped", 0);
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void verifyWildcardQueriesSucceed(RegionTestableType regionTestType) {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));
    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));
    executeTextSearch(accessor, "*", "*", 3);
    executeTextSearch(accessor, "*:*", "text", 3);
    executeTextSearch(accessor, "*:*", "XXX", 3);
    executeTextSearch(accessor, "*", "text", 3);
  }

  @Test
  @Parameters(method = "getListOfRegionTestTypes")
  public void verifySpaceQueriesFail(RegionTestableType regionTestType) {
    SerializableRunnableIF createIndex = () -> {
      LuceneService luceneService = LuceneServiceProvider.get(getCache());
      luceneService.createIndexFactory().addField("text").create(INDEX_NAME, REGION_NAME);
    };
    dataStore1.invoke(() -> initDataStore(createIndex, regionTestType));
    dataStore2.invoke(() -> initDataStore(createIndex, regionTestType));
    accessor.invoke(() -> initAccessor(createIndex, regionTestType));
    putDataInRegion(accessor);
    assertTrue(waitForFlushBeforeExecuteTextSearch(accessor, 60000));
    assertTrue(waitForFlushBeforeExecuteTextSearch(dataStore1, 60000));
    executeTextSearchWithExpectedException(accessor, " ", "*", LuceneQueryException.class);
    executeTextSearchWithExpectedException(accessor, " ", "text", LuceneQueryException.class);
    executeTextSearchWithExpectedException(accessor, " ", "XXX", LuceneQueryException.class);
  }

  protected void putDataInRegion(VM vm) {
    vm.invoke(() -> {
      final Cache cache = getCache();
      Region<Object, Object> region = cache.getRegion(REGION_NAME);
      region.put(1, new TestObject("hello world"));
      region.put(113, new TestObject("hi world"));
      region.put(2, new TestObject("goodbye world"));
    });
  }

  @Override
  public Properties getDistributedSystemProperties() {
    Properties result = super.getDistributedSystemProperties();
    String filter = (String) result.get(ConfigurationProperties.SERIALIZABLE_OBJECT_FILTER);
    filter += ";org.apache.geode.cache.lucene.LuceneQueriesDUnitTest*";
    result.put(ConfigurationProperties.SERIALIZABLE_OBJECT_FILTER, filter);
    return result;
  }
}

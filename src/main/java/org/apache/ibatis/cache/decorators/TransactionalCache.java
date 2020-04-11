/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class TransactionalCache implements Cache {

  private static final Log log = LogFactory.getLog(TransactionalCache.class);

  private final Cache delegate;
  private boolean clearOnCommit;
  private final Map<Object, Object> entriesToAddOnCommit;
  private final Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<>();
    this.entriesMissedInCache = new HashSet<>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    // 注意这里拿是在 delegate 中拿的而不是 entriesToAddOnCommit 中
    Object object = delegate.getObject(key);
    if (object == null) {
      // 记录未命中缓存的 CacheKey，后面 commit 的时候会放置一个 null 值进主缓存
      entriesMissedInCache.add(key);
    }
    // issue #146: https://github.com/mybatis/mybatis-3/issues/146
    // 这里是防止 事务提交后清除缓存 这个动作已经执行了，但是缓存中还是能拿到东西。
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public void putObject(Object key, Object object) {
    // 这里的putObject 方法只是将 obj 放到了当前事务的缓存中即 entriesToAddOnCommit 中。
    // 所以事务不提交的话，在 delegate 中是拿不到的。用以保证事务缓存隔离
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    // 提交时清除的 标志位
    clearOnCommit = true;
    // 当前内部缓存清除
    entriesToAddOnCommit.clear();
  }

  public void commit() {
    if (clearOnCommit) {
      delegate.clear();
    }
    // 当事务提交时，这里统一刷缓存
    flushPendingEntries();
    reset();
  }

  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  /**
   * 这个方法是将本次事务缓存中的所有缓存刷到 delegate 中
   * 做到了缓存的事务隔离
   */
  private void flushPendingEntries() {
    // 遍历 entry
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      // 如果未命中的 CacheKey 在 当前内部缓存中没有的话，则放置一个 null 进主缓存
      // 目的应该是防止缓存击穿（大量查询一个不存在的值）
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      try {
        delegate.removeObject(entry);
      } catch (Exception e) {
        log.warn("Unexpected exception while notifiying a rollback to the cache adapter. "
            + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
      }
    }
  }

}

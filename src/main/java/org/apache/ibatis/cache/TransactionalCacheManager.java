/**
 *    Copyright 2009-2019 the original author or authors.
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
package org.apache.ibatis.cache;

import java.util.HashMap;
import java.util.Map;

import org.apache.ibatis.cache.decorators.TransactionalCache;

/**
 * @author Clinton Begin
 */
public class TransactionalCacheManager {

  private final Map<Cache, TransactionalCache> transactionalCaches = new HashMap<>();

  public void clear(Cache cache) {
    getTransactionalCache(cache).clear();
  }

  public Object getObject(Cache cache, CacheKey key) {
    // 这里看上去是先根据 Cache 拿出内部 TransactionalCache，然后再从 TransactionalCache 中拿值。
    // 但实际上 TransactionalCache 是一个装饰器类，它负责装饰了 cache ，最终还是从 cache 中拿的值
    return getTransactionalCache(cache).getObject(key);
  }

  public void putObject(Cache cache, CacheKey key, Object value) {
    // 这里看上去跟上面的 getObject 方法一样，但是这里却不是给 cache put 值,
    // 而是给 TransactionalCache 内部维护的一个 HashMap 类型的变量 entriesToAddOnCommit put值
    // 这么做是为了保证事务的隔离性，缓存同样要等事务提交后统一刷到公共 cache 中
    getTransactionalCache(cache).putObject(key, value);
  }

  public void commit() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.commit();
    }
  }

  public void rollback() {
    for (TransactionalCache txCache : transactionalCaches.values()) {
      txCache.rollback();
    }
  }

  private TransactionalCache getTransactionalCache(Cache cache) {
    // 这里的 computeIfAbsent 相当于如下代码：
    /*
      if(null == transactionalCaches.get(cache)){
        transactionalCaches.put(cache, new TransactionalCache(cache));
      }
      或
      transactionalCaches.computeIfAbsent(cache, k -> new TransactionalCache(k));
     */
    return transactionalCaches.computeIfAbsent(cache, TransactionalCache::new);
  }

}

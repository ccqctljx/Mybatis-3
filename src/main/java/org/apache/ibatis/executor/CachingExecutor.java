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
package org.apache.ibatis.executor;

import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.cache.TransactionalCacheManager;
import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class CachingExecutor implements Executor {

  private final Executor delegate;
  private final TransactionalCacheManager tcm = new TransactionalCacheManager();

  // 构造方法中传入执行类，采用装饰器模式，对传进来的 Executor 进行增强
  public CachingExecutor(Executor delegate) {
    this.delegate = delegate;
    delegate.setExecutorWrapper(this);
  }

  @Override
  public Transaction getTransaction() {
    return delegate.getTransaction();
  }

  @Override
  public void close(boolean forceRollback) {
    try {
      //issues #499, #524 and #573
      if (forceRollback) {
        tcm.rollback();
      } else {
        tcm.commit();
      }
    } finally {
      delegate.close(forceRollback);
    }
  }

  @Override
  public boolean isClosed() {
    return delegate.isClosed();
  }

  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    // 先根据需要看是否清除缓存
    flushCacheIfRequired(ms);
    // 在调用 被包装类的 update 方法
    return delegate.update(ms, parameterObject);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameterObject);
    // 根据 ms、参数、分页参数、sql 生成这个 statement 唯一的缓存 key
    CacheKey key = createCacheKey(ms, parameterObject, rowBounds, boundSql);
    return query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    flushCacheIfRequired(ms);
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    // 这里是看我们有没有定义 Cache 对象，也就是我们在 Mapper 文件中有没有定义 <cache/> 标签
    // 如果有标签，在读取 Mapper 文件时会创建 Cache 对象来存储这个 Mapper 文件中所有需要缓存的东西
    Cache cache = ms.getCache();
    if (cache != null) {
      // 如果标签属性上标注了 flushCache="true" ,这里会先清空缓存
      flushCacheIfRequired(ms);
      if (ms.isUseCache() && resultHandler == null) {
        // 确定本条不是一个有 OutParams 的存储过程，否则抛出异常
        ensureNoOutParams(ms, boundSql);
        @SuppressWarnings("unchecked")
        // 这里 TransactionalCacheManager 维护了一个以 Cache 为键，TransactionalCache 为值的一个 Map
        // 内部方法是尝试从 cache 中拿值
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          // 这里的 delegate 代表的是根据ExecutorType创建的几大执行器，例如 SimpleExecutor。
          // 也就是说，他这里只不过是先根据是否开启二级缓存，尝试是否能从缓存中拿到数据，
          // 但是如果真的没拿出来的话，真正查询还是交由传入的执行器来执行
          // 也就是传说中的 装饰器模式
          list = delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          // 这里是往 TransactionalCache 中赋值
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        return list;
      }
    }
    return delegate.query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }

  @Override
  public List<BatchResult> flushStatements() throws SQLException {
    return delegate.flushStatements();
  }

  @Override
  public void commit(boolean required) throws SQLException {
    delegate.commit(required);
    tcm.commit();
  }

  @Override
  public void rollback(boolean required) throws SQLException {
    try {
      delegate.rollback(required);
    } finally {
      if (required) {
        tcm.rollback();
      }
    }
  }

  private void ensureNoOutParams(MappedStatement ms, BoundSql boundSql) {
    if (ms.getStatementType() == StatementType.CALLABLE) {
      for (ParameterMapping parameterMapping : boundSql.getParameterMappings()) {
        if (parameterMapping.getMode() != ParameterMode.IN) {
          throw new ExecutorException("Caching stored procedures with OUT params is not supported.  Please configure useCache=false in " + ms.getId() + " statement.");
        }
      }
    }
  }

  @Override
  public CacheKey createCacheKey(MappedStatement ms, Object parameterObject, RowBounds rowBounds, BoundSql boundSql) {
    return delegate.createCacheKey(ms, parameterObject, rowBounds, boundSql);
  }

  @Override
  public boolean isCached(MappedStatement ms, CacheKey key) {
    return delegate.isCached(ms, key);
  }

  @Override
  public void deferLoad(MappedStatement ms, MetaObject resultObject, String property, CacheKey key, Class<?> targetType) {
    delegate.deferLoad(ms, resultObject, property, key, targetType);
  }

  @Override
  public void clearLocalCache() {
    delegate.clearLocalCache();
  }

  private void flushCacheIfRequired(MappedStatement ms) {
    // 获取当前缓存
    Cache cache = ms.getCache();
    // 除非配置，不然 insert | update | delete 三大标签的 flushCacheRequired 默认为 true
    // 这里可以看加载生成 Mapper 的默认赋值 ->
    // org.apache.ibatis.builder.xml.XMLStatementBuilder.parseStatementNode ->
    // org.apache.ibatis.builder.MapperBuilderAssistant.addMappedStatement
    if (cache != null && ms.isFlushCacheRequired()) {
      // 调用缓存清除方法
      tcm.clear(cache);
    }
  }

  @Override
  public void setExecutorWrapper(Executor executor) {
    throw new UnsupportedOperationException("This method should not be called");
  }

}

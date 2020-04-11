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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

import org.apache.ibatis.reflection.ArrayUtil;

/**
 * @author Clinton Begin
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  public static final CacheKey NULL_CACHE_KEY = new CacheKey(){
    @Override
    public void update(Object object) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
    @Override
    public void updateAll(Object[] objects) {
      throw new CacheException("Not allowed to update a null cache key instance.");
    }
  };

  // 乘数，固定初始值质数37，不会变
  private static final int DEFAULT_MULTIPLIER = 37;

  // 当前hashCode值，初始值是质数17，
  private static final int DEFAULT_HASHCODE = 17;

  // 乘数，默认值为质数37，不会变
  private final int multiplier;
  // 当前hashCode值，默认值为质数17，
  private int hashcode;
  // 所有更新对象的初始hashCode的和
  private long checksum;
  // 更新的对象总数
  private int count;

  /*
    8/21/2017 - Sonar lint flags this as needing to be marked transient.
    While true if content is not serializable,
    this is not always true and thus should not be marked transient.
  */
  // 已更新的所有 obj 的列表
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLIER;
    this.count = 0;
    this.updateList = new ArrayList<>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  public void update(Object object) {
    // 先计算传进来的这个 obj 的基础 HashCode，如果为空的话则是 1
    int baseHashCode = object == null ? 1 : ArrayUtil.hashCode(object);
    // 记录更新个数
    count++;
    // 计算 hashCode 的总和
    checksum += baseHashCode;
    // 将基础 HashCode 跟更新个数相乘
    baseHashCode *= count;
    // 最终得到新的 hashcode 为 固定数字 37 * 最新 hashcode 再加上 计算后的参数对象的 hashcode
    hashcode = multiplier * hashcode + baseHashCode;
    // 将传进来的 obj 放到已更新列表中
    updateList.add(object);
  }

  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    if (checksum != cacheKey.checksum) {
      return false;
    }
    if (count != cacheKey.count) {
      return false;
    }

    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (!ArrayUtil.equals(thisObject, thatObject)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringJoiner returnValue = new StringJoiner(":");
    returnValue.add(String.valueOf(hashcode));
    returnValue.add(String.valueOf(checksum));
    updateList.stream().map(ArrayUtil::toString).forEach(returnValue::add);
    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<>(updateList);
    return clonedCacheKey;
  }

}

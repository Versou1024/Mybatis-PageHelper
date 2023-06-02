/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 abel533@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.github.pagehelper.cache;

import com.github.pagehelper.util.StringUtil;
import org.apache.ibatis.cache.decorators.FifoCache;
import org.apache.ibatis.cache.impl.PerpetualCache;
import org.apache.ibatis.mapping.CacheBuilder;

import java.util.Properties;

/**
 * Simple MyBatis Cache
 *
 * @author liuzh
 */
public class SimpleCache<K, V> implements Cache<K, V> {
    // 99%的情况使用的Cache

    private final org.apache.ibatis.cache.Cache CACHE; // 很明显SimpleCache，其实是根据mybatis的cache来创建的，常见的例如LruCache、FifoCache，还有最常用的永久缓存PerpetualCache
    // 底层实际上

    public SimpleCache(Properties properties, String prefix) {
        CacheBuilder cacheBuilder = new CacheBuilder("SQL_CACHE");
        String typeClass = properties.getProperty(prefix + ".typeClass");
        if (StringUtil.isNotEmpty(typeClass)) {
            try {
                cacheBuilder.implementation((Class<? extends org.apache.ibatis.cache.Cache>) Class.forName(typeClass));
            } catch (ClassNotFoundException e) {
                cacheBuilder.implementation(PerpetualCache.class);
            }
        } else {
            cacheBuilder.implementation(PerpetualCache.class); // ❤❤ 绝大部分情况，都不会去配置 perfix.typeClass 因此使用PerpetualCache永久缓存
        }
        String evictionClass = properties.getProperty(prefix + ".evictionClass");
        if (StringUtil.isNotEmpty(evictionClass)) {
            try {
                cacheBuilder.addDecorator((Class<? extends org.apache.ibatis.cache.Cache>) Class.forName(evictionClass));
            } catch (ClassNotFoundException e) {
                cacheBuilder.addDecorator(FifoCache.class);
            }
        } else {
            cacheBuilder.addDecorator(FifoCache.class); // ❤❤ 默认使用驱逐策略是Fifo类型的，还有一种类型是Lru
        }
        /*
        MyBatis 中的 Cache 是用来缓存查询结果以提高查询效率的，其中 flushInterval 属性表示缓存的刷新间隔时间，单位为毫秒。它的作用是定时清空缓存，以保证缓存中的数据和数据库中的数据保持一致。
        flushInterval 的默认值为 -1，表示不定时刷新缓存，而是在缓存满了之后才会刷新。如果设置了 flushInterval 的值，则表示在指定的时间间隔内会自动刷新缓存。
        例如，如果将 flushInterval 设置为 60000 毫秒（即 1 分钟），则表示每隔 1 分钟会清空一次缓存，以保证缓存中的数据和数据库中的数据保持一致。
         */
        String flushInterval = properties.getProperty(prefix + ".flushInterval");
        if (StringUtil.isNotEmpty(flushInterval)) {
            cacheBuilder.clearInterval(Long.parseLong(flushInterval));
        }
        String size = properties.getProperty(prefix + ".size");
        if (StringUtil.isNotEmpty(size)) {
            cacheBuilder.size(Integer.parseInt(size)); // ❤❤ 缓存大小
        }
        cacheBuilder.properties(properties);
        CACHE = cacheBuilder.build();
    }

    @Override
    public V get(K key) {
        Object value = CACHE.getObject(key);
        if (value != null) {
            return (V) value;
        }
        return null;
    }

    @Override
    public void put(K key, V value) {
        CACHE.putObject(key, value);
    }
}

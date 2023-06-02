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

package com.github.pagehelper;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.RowBounds;

import java.util.List;
import java.util.Properties;

/**
 * 数据库方言，针对不同数据库进行实现
 *
 * @author liuzh
 */
public interface Dialect {
    // 掌握主要方法：
    // 1. skip 检查是否需要做分页

    // 2. beforeCount 执行分页前，返回 true 会进行 count 总数查询，false 会继续下面的 beforePage 判断
    // 3. getCountSql 生成 count 查询 sql
    // 4. afterCount 执行完 count 查询后

    // 5. beforePage 执行分页前，返回 true 会进行 分页 查询，false 会返回默认的查询结果
    // 6. getPageSql 生成 分页 查询 sql （在原有的sql上，根据数据库类型，例如MySQL，追加 limit n,m 的操作）
    // 7. afterPage 执行完 分页 查询后，拦截器中直接 return 该方法的返回值

    // 8. afterAll 钩子方法：在所有方法执行完之后再执行


    /**
     * 跳过 count 和 分页查询
     *
     * @param ms              MappedStatement
     * @param parameterObject 方法参数
     * @param rowBounds       分页参数
     * @return true 跳过，返回默认查询结果，false 执行分页查询
     */
    boolean skip(MappedStatement ms, Object parameterObject, RowBounds rowBounds);

    /**
     * 执行分页前，返回 true 会进行 count 查询，false 会继续下面的 beforePage 判断
     *
     * @param ms              MappedStatement
     * @param parameterObject 方法参数
     * @param rowBounds       分页参数
     * @return
     */
    boolean beforeCount(MappedStatement ms, Object parameterObject, RowBounds rowBounds);

    /**
     * 生成 count 查询 sql
     *
     * @param ms              MappedStatement
     * @param boundSql        绑定 SQL 对象
     * @param parameterObject 方法参数
     * @param rowBounds       分页参数
     * @param countKey        count 缓存 key
     * @return
     */
    String getCountSql(MappedStatement ms, BoundSql boundSql, Object parameterObject, RowBounds rowBounds, CacheKey countKey);

    /**
     * 执行完 count 查询后
     *
     * @param count           查询结果总数
     * @param parameterObject 接口参数
     * @param rowBounds       分页参数
     * @return true 继续分页查询，false 直接返回
     */
    boolean afterCount(long count, Object parameterObject, RowBounds rowBounds);

    /**
     * 处理查询参数对象
     *
     * @param ms              MappedStatement
     * @param parameterObject
     * @param boundSql
     * @param pageKey
     * @return
     */
    Object processParameterObject(MappedStatement ms, Object parameterObject, BoundSql boundSql, CacheKey pageKey);

    /**
     * 执行分页前，返回 true 会进行分页查询，false 会返回默认查询结果
     *
     * @param ms              MappedStatement
     * @param parameterObject 方法参数
     * @param rowBounds       分页参数
     * @return
     */
    boolean beforePage(MappedStatement ms, Object parameterObject, RowBounds rowBounds);

    /**
     * 生成分页查询 sql
     *
     * @param ms              MappedStatement
     * @param boundSql        绑定 SQL 对象
     * @param parameterObject 方法参数
     * @param rowBounds       分页参数
     * @param pageKey         分页缓存 key
     * @return
     */
    String getPageSql(MappedStatement ms, BoundSql boundSql, Object parameterObject, RowBounds rowBounds, CacheKey pageKey);

    /**
     * 分页查询后，处理分页结果，拦截器中直接 return 该方法的返回值
     *
     * @param pageList        分页查询结果
     * @param parameterObject 方法参数
     * @param rowBounds       分页参数
     * @return
     */
    Object afterPage(List pageList, Object parameterObject, RowBounds rowBounds);

    /**
     * 完成所有任务后
     */
    void afterAll();

    /**
     * 设置参数
     *
     * @param properties 插件属性
     */
    void setProperties(Properties properties);
}

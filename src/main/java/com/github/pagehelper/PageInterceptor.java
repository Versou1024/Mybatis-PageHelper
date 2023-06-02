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

import com.github.pagehelper.cache.Cache;
import com.github.pagehelper.cache.CacheFactory;
import com.github.pagehelper.util.ExecutorUtil;
import com.github.pagehelper.util.MSUtils;
import com.github.pagehelper.util.StringUtil;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Mybatis - 通用分页拦截器
 * <p>
 * GitHub: https://github.com/pagehelper/Mybatis-PageHelper
 * <p>
 * Gitee : https://gitee.com/free/Mybatis_PageHelper
 *
 * @author liuzh/abel533/isea533
 * @version 5.0.0
 */
@SuppressWarnings({"rawtypes", "unchecked"})
@Intercepts(
        {
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
                @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
        }
)
public class PageInterceptor implements Interceptor {
    // ❤❤❤ 核心类：如果需要使用PageInterceptor，请注入到SqlSessionFactory中

    private volatile Dialect dialect; // 这里99.99%都是 PageHelper
    private String countSuffix = "_COUNT"; // 默认后缀 _COUNT
    protected Cache<String, MappedStatement> msCountMap = null;
    private String default_dialect_class = "com.github.pagehelper.PageHelper";

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        try {
            Object[] args = invocation.getArgs();
            MappedStatement ms = (MappedStatement) args[0];
            Object parameter = args[1];
            RowBounds rowBounds = (RowBounds) args[2];
            ResultHandler resultHandler = (ResultHandler) args[3];
            Executor executor = (Executor) invocation.getTarget();
            CacheKey cacheKey;
            BoundSql boundSql;
            //由于逻辑关系，只会进入一次
            if (args.length == 4) {
                //4 个参数时
                boundSql = ms.getBoundSql(parameter);
                cacheKey = executor.createCacheKey(ms, parameter, rowBounds, boundSql);
            } else {
                //6 个参数时
                cacheKey = (CacheKey) args[4];
                boundSql = (BoundSql) args[5];
            }
            checkDialectExists();

            // ❤❤❤❤❤ 核心拦截的过程
            List resultList;
            // 调用方法判断是否需要进行分页，如果不需要，直接返回结果
            // 判断是否跳过的几种情况
            //  a. 在执行查询操作之前，执行 PageHelper#startPage 操作
            //      需要注意的是： PageHelper#startPage 操作后，只有后续第一个查询操作都会变成分页查询操作，剩下的查询操作就是普通查询操作不会被拦截的，除非有以下情况
            //  b. 检查mapper方法中，是否有设定RowBounds，然后再根据 offsetAsPageNum 处理构造 Page
            //      b.a PageRowBounds 类型的行数量限制，还额外提供count参数的配置，即配置是否查询total参数
            //  c. 形参有直接实现IPage (他要求Mapper方法只有一个形参，并且没有使用@Param注解才ok的哦)
            if (!dialect.skip(ms, parameter, rowBounds)) {
                // 判断是否需要进行 count
                // !page.isOrderByOnly() && page.isCount(); -- 通过判断page的count参数是否为true，并且不是只要求排序（通常orderByOnly都是false，没人配这个玩意儿）
                // 因此只要 page 的 count 参数为 true 就会去 count查询
                if (dialect.beforeCount(ms, parameter, rowBounds)) {
                    // ❤❤ 查询总数，这里负责做 count 查询
                    Long count = count(executor, ms, parameter, rowBounds, resultHandler, boundSql);
                    // 处理查询总数，返回 true 时继续分页查询，false 时直接返回
                    // ❤ 会将 count 参数设置到 Page#total 属性中
                    //  并且通过pageNum和pageSize查看起始行offset是否已经超过count，是的话，就返回false，直接结束，不再执行pageQuery分页查询操作
                    //  或者，pageSize 小于 0， 那么也不会去执行 分页查询，也返回 false，
                    if (!dialect.afterCount(count, parameter, rowBounds)) {
                        //当查询总数为 0 时，直接返回空的结果
                        return dialect.afterPage(new ArrayList(), parameter, rowBounds);
                    }
                }
                // ❤❤ 这里负责：做 pageQuery 分页查询，resultList 是查询的结果集
                resultList = ExecutorUtil.pageQuery(dialect, executor,
                        ms, parameter, rowBounds, resultHandler, boundSql, cacheKey);
            } else {
                //rowBounds用参数值，不使用分页插件处理时，仍然支持默认的内存分页
                resultList = executor.query(ms, parameter, rowBounds, resultHandler, cacheKey, boundSql);
            }
            // ❤❤ 将分页查询的结果集resultList设置到Page中，注意到Page其实是继承的ArrayList的哦
            return dialect.afterPage(resultList, parameter, rowBounds);
        } finally {
            if(dialect != null){
                // ❤❤ 另一个关键点，那就是每次执行完后，必须将  ThreadLocal<Page> 中的 page 参数给删除调，防止下次查询被误认为还要做分页查询
                dialect.afterAll();
            }
        }
    }

    /**
     * Spring bean 方式配置时，如果没有配置属性就不会执行下面的 setProperties 方法，就不会初始化
     * <p>
     * 因此这里会出现 null 的情况 fixed #26
     */
    private void checkDialectExists() {
        if (dialect == null) {
            synchronized (default_dialect_class) {
                if (dialect == null) {
                    setProperties(new Properties());
                }
            }
        }
    }

    private Long count(Executor executor, MappedStatement ms, Object parameter,
                       RowBounds rowBounds, ResultHandler resultHandler,
                       BoundSql boundSql) throws SQLException {
        // note: ❤❤❤❤❤ -- 这里需要注意一点：那就是 count查询 和 分页查询 实际上在一个SqlSession中执行。
        // 虽然按照道理锁，多个不同的方法都应该被同一个DefaultSqlSession中执行处理，但现在好像每个SqlSession都是重新创建的
        // 这意味着每个执行一个sql，就要SqlSession都必须重新建立起来，需要去看看 【这里的注释，和PageHelper无关，仅仅记录】


        // countsuffix的默认后缀是：_COUNT
        String countMsId = ms.getId() + countSuffix;
        Long count; // ❤❤❤❤❤ count 用来查询总数的哦
        //先判断是否存在手写的 count 查询
        MappedStatement countMs = ExecutorUtil.getExistedMappedStatement(ms.getConfiguration(), countMsId);
        if (countMs != null) {
            // 1. 如果有手写的count查询，就直接执行，无法填充
            count = ExecutorUtil.executeManualCount(executor, countMs, parameter, boundSql, resultHandler);
        } else {
            // 2. 如果没有手写的count查询，就通过替换为select count(0)
            countMs = msCountMap.get(countMsId); // ❤❤❤ 缓存查询：这是一个很关键的处理
            //自动创建
            if (countMs == null) { // 没有获取到，
                // 根据当前的 ms 创建一个返回值为 Long 类型的 ms
                // ❤❤❤❤ 构建的count查询语句：缓存起来啦，后续可以根据countMsId直接去msCountMap中获取，就不需要再次来构造啦
                countMs = MSUtils.newCountMappedStatement(ms, countMsId);
                msCountMap.put(countMsId, countMs);
            }
            // 2.1 构造完后，老样子就是去执行他 -- ❤❤❤
            count = ExecutorUtil.executeAutoCount(dialect, executor, countMs, parameter, boundSql, rowBounds, resultHandler);
        }
        return count;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 设置属性的位置：wms系统的属性设置如下
        /*
        helperDialect: mysql
        reasonable: false
        supportMethodsArguments: true
        params: count=countSql
         */

        // 1. 创建缓存 count mapperStatement 的容器 msCountMap
        msCountMap = CacheFactory.createCache(properties.getProperty("msCountCache"), "ms", properties);

        // 2. 确定使用的数据库类型，选择合适的执行者 dialect
        String dialectClass = properties.getProperty("dialect");
        if (StringUtil.isEmpty(dialectClass)) {
            dialectClass = default_dialect_class; // ❤❤ 默认是使用 PageHelper（另外一个核心点，需要掌握理解）
        }
        try {
            Class<?> aClass = Class.forName(dialectClass);
            dialect = (Dialect) aClass.newInstance();
        } catch (Exception e) {
            throw new PageException(e);
        }
        dialect.setProperties(properties); // 会将配置传递给dialect(通常是PageHelper)

        // 确定 countSuffix
        String countSuffix = properties.getProperty("countSuffix");
        if (StringUtil.isNotEmpty(countSuffix)) {
            this.countSuffix = countSuffix;
        }
    }

}

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

package com.github.pagehelper.page;

import com.github.pagehelper.IPage;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageRowBounds;
import com.github.pagehelper.util.PageObjectUtil;
import com.github.pagehelper.util.StringUtil;
import org.apache.ibatis.session.RowBounds;

import java.util.Properties;

/**
 * Page 参数信息
 *
 * @author liuzh
 */
public class PageParams {

    // 分页查询的相关参数

    //RowBounds参数offset作为PageNum使用 - 默认不使用
    protected boolean offsetAsPageNum = false;
    //RowBounds是否进行count查询 - 默认不查询
    protected boolean rowBoundsWithCount = false;
    //当设置为true的时候，如果pagesize设置为0（或RowBounds的limit=0），就不执行分页，返回全部结果
    // 默认是false的哦
    protected boolean pageSizeZero = false;
    //分页合理化
    protected boolean reasonable = false;
    // 是否支持接口参数来传递分页参数，默认false
    // 不过新夏晖wms系统，通过将这个参数设置为true啦
    protected boolean supportMethodsArguments = false;
    //默认count(0)
    protected String countColumn = "0";

    /**
     * 获取分页参数
     *
     * @param parameterObject
     * @param rowBounds
     * @return
     */
    public Page getPage(Object parameterObject, RowBounds rowBounds) {
        // ❤❤❤❤❤❤❤❤❤❤❤❤❤❤
        // 0. 这个步骤十分关键：为什么这样说
        // 夏晖WMS系统中，例如通过 BaseController#startPage() 操作获取Request中的请求参数pageSize、pageNum、orderBy字段
        // 然后调用 PageHelper.startPage(pageSize,pageNum) 该方法最终会生成一个 Page 对象，❤❤❤ 然后设置到 ThreadLocal<Page> 对象中
        // 也就是说下一次的查询操作，被 PageInterceptor 拦截到后，如果前面有调用 PageHelper#startPage 操作就会执行分页，而不会跳过Skip
        // 当然下面还提供两种方式来检查是否需要做分页查询，总结就是以下几种方式，
        //  a. 在执行查询操作之前，执行 PageHelper#startPage 操作，缺点就是如果 PageHelper#startPage 操作后，后续第一个查询操作都会变成分页查询操作，剩下的查询操作就是普通查询操作不会被拦截的，除非有以下情况
        //  b. 检查mapper方法中，是否有设定RowBounds，处理构造 Page（根据参数offsetAsPageNum的不同而处理起来不同）
        //      b.a PageRowBounds 类型的行数量限制，还额外提供count参数的配置，即配置是否查询total参数
        //  c. 形参有直接实现IPage (他要求Mapper方法只有一个形参，并且没有使用@Param注解才ok的哦)
        Page page = PageHelper.getLocalPage();
        // 1. 当前线程第一次获取Page信息，因此ThreadLocal中无法找到Page参数
        // 如果已有Page参数将不会创建Page，而是沿用Page参数
        if (page == null) {
            // 其实一般我们都不会在mapper方法中，使用RowBounds参数来告知Limit和Offset，所以在MapperMethod上会自动填充为RowBounds.DEFAULT来填充该参数
            // ❤❤ 因此如果 rowBounds != RowBounds.DEFAULT 说明开发者传递有RowBounds参数，那就从RowBounds参数中获取page的相关信息
            if (rowBounds != RowBounds.DEFAULT) {
                if (offsetAsPageNum) {
                    // ❤❤ 如果offsetAsPageNum设为true，意味着offset将直接作为pageNum，比如 offset = 20, limit = 10,那么 pageNum = 20， pageSize = 10
                    page = new Page(rowBounds.getOffset(), rowBounds.getLimit(), rowBoundsWithCount);
                } else {
                    // offsetAsPageNum 默认是false的哦，意味着不改变offset的含义，比如 offset = 20， limit = 10， 那么 pageNum = 3， pageSize = 10。
                    page = new Page(new int[]{rowBounds.getOffset(), rowBounds.getLimit()}, rowBoundsWithCount);
                    //offsetAsPageNum=false的时候，由于PageNum问题，不能使用reasonable，这里会强制为false
                    page.setReasonable(false);
                }
                if(rowBounds instanceof PageRowBounds){
                    PageRowBounds pageRowBounds = (PageRowBounds)rowBounds;
                    page.setCount(pageRowBounds.getCount() == null || pageRowBounds.getCount());
                }
                // ❤❤❤❤❤ 实际上，大部分情况中，我们都不会向mapper方法中注入RowBounds，因此会在这里执行生成Page
                // supportMethodsArguments，是否支持接口参数来传递分页参数，
                // ❤❤❤❤❤ supportMethodsArguments 被wms系统设置为true
            } else if(parameterObject instanceof IPage || supportMethodsArguments){
                try {
                    page = PageObjectUtil.getPageFromObject(parameterObject, false);
                } catch (Exception e) {
                    return null;
                }
            }
            if(page == null){
                return null; // 如果仍然没有Page，那就返回null吧
            }
            PageHelper.setLocalPage(page); // 有的话，和当前线程绑定Page参数
        }
        //分页合理化
        if (page.getReasonable() == null) {
            page.setReasonable(reasonable);
        }
        //当设置为true的时候，如果pagesize设置为0（或RowBounds的limit=0），就不执行分页，返回全部结果
        if (page.getPageSizeZero() == null) {
            page.setPageSizeZero(pageSizeZero);
        }
        return page;
    }

    public void setProperties(Properties properties) {
        //offset作为PageNum使用
        String offsetAsPageNum = properties.getProperty("offsetAsPageNum");
        this.offsetAsPageNum = Boolean.parseBoolean(offsetAsPageNum);
        //RowBounds方式是否做count查询
        String rowBoundsWithCount = properties.getProperty("rowBoundsWithCount");
        this.rowBoundsWithCount = Boolean.parseBoolean(rowBoundsWithCount);
        //当设置为true的时候，如果pagesize设置为0（或RowBounds的limit=0），就不执行分页
        String pageSizeZero = properties.getProperty("pageSizeZero");
        this.pageSizeZero = Boolean.parseBoolean(pageSizeZero);
        // 分页合理化，true开启，如果分页参数不合理会自动修正。默认false不启用
        // 比如：wms系统中reasonable就是false，即分页参数不合理是不会秀城的
        String reasonable = properties.getProperty("reasonable");
        this.reasonable = Boolean.parseBoolean(reasonable);
        //是否支持接口参数来传递分页参数，默认false
        String supportMethodsArguments = properties.getProperty("supportMethodsArguments");
        this.supportMethodsArguments = Boolean.parseBoolean(supportMethodsArguments);
        //默认count列
        String countColumn = properties.getProperty("countColumn");
        if(StringUtil.isNotEmpty(countColumn)){
            this.countColumn = countColumn;
        }
        //当offsetAsPageNum=false的时候，不能
        //参数映射
        PageObjectUtil.setParams(properties.getProperty("params"));
    }

    public boolean isOffsetAsPageNum() {
        return offsetAsPageNum;
    }

    public boolean isRowBoundsWithCount() {
        return rowBoundsWithCount;
    }

    public boolean isPageSizeZero() {
        return pageSizeZero;
    }

    public boolean isReasonable() {
        return reasonable;
    }

    public boolean isSupportMethodsArguments() {
        return supportMethodsArguments;
    }

    public String getCountColumn() {
        return countColumn;
    }
}

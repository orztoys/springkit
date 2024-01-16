package org.orztoys.mybatisplus.wquery;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 基于Mybatis-plus的简化查询Controller注解
 * 
 * <li>自动装载查询参数，需要用DynamicQuery(querys={"name"})打开此字段<br/>
 * 自动收集URL上的parameter装进<code>QueryWrapper&lt;T&gt;</code>,字段名与<code>T</code>中的字段对应<br/>
 * 比如<code>T</code>中有字段name,则会读取?name=aaa的值
 * 
 * <li>Controller方法上有IPage<T>的参数 且 URL参数存在page[页码],size[页大小]，自动装载数据到IPage对象
 * 
 * 
 * <pre>
 * 使用例子，会把state,uname,uid自动装载进query对象，page,size自动装载进rowPage对象
 * <code>
 * &#64;WebQuery(value = {
 * 		&#64;QueryResolver(name = "state", value = StateValueResolve.class),
 * 		&#64;QueryResolver(name = "uid", inEntity = "id") }, 
 * querys = { "state", "uname", "uid" })
 * public CommonResult&lt;IPage&lt;SysUserEntity&gt;&gt; list(Page&lt;SysUserEntity&gt; rowPage, QueryWrapper&lt;SysUserEntity&gt; query) {
 * </code>
 * </pre>
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
public @interface WebQuery {
	
	String[] likes() default {};

	String[] querys() default {};

	String[] asc() default {};

	String[] desc() default {};

	QueryResolver[] value() default {};
	
	

}

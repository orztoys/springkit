package org.orztoys.mybatisplus.wquery;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@Repeatable(WebQuery.class)
public @interface QueryResolver {
	/**
	 * URL参数名
	 * 
	 * @return
	 */
	String name();

	/**
	 * 对应Entity的实际字段名
	 * 
	 * @return
	 */
	String inEntity() default "";

	/**
	 * 自定义值解析器
	 * 
	 * @return
	 */
	Class<? extends QueryValueResolver> value() default QueryValueResolver.class;

}

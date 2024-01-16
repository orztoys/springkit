package org.orztoys.mybatis.autofill;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.ibatis.mapping.SqlCommandType;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

@Order(Ordered.LOWEST_PRECEDENCE)
public class AutoFillColumnBootstrap implements ApplicationListener<ContextRefreshedEvent> {

	static final ConcurrentMap<SqlCommandType, List<AutoFillColumnValueResolve>> COL_RESOLVE_LIST = new ConcurrentHashMap<>();

	@Bean
	@ConditionalOnMissingBean(AutoFillUpdateInterceptor.class)
	public AutoFillUpdateInterceptor register() {
		final AutoFillUpdateInterceptor plugin = new AutoFillUpdateInterceptor();
		return plugin;
	}

	public AutoFillColumnValueResolve creatorAutoColumnBean() {
		return new AutoFillColumnValueResolve() {
			@Override
			public SqlCommandType[] supporSqlType() {
				return new SqlCommandType[] { SqlCommandType.INSERT };
			}

			@Override
			public String columnName() {
				return "creator";
			}

			@Override
			public Object resolve(final SqlCommandType sct, final String table) {
				return CurrentContextHolder.CURRENT.get();
			}

			@Override
			public boolean support(final SqlCommandType sct, final String table) {
				return true;
			}

		};
	}

	public AutoFillColumnValueResolve updaterAutoColumnBean() {
		return new AutoFillColumnValueResolve() {
			@Override
			public SqlCommandType[] supporSqlType() {
				return new SqlCommandType[] { SqlCommandType.UPDATE };
			}

			@Override
			public String columnName() {
				return "updater";
			}

			@Override
			public Object resolve(final SqlCommandType sct, final String table) {
				return CurrentContextHolder.CURRENT.get();
			}

			@Override
			public boolean support(final SqlCommandType sct, final String table) {
				return true;
			}

		};
	}

	private void registerValueResolveBean(final ConfigurableListableBeanFactory beanFactory) {
		final AutoFillColumnValueResolve bean = beanFactory.getBean(AutoFillColumnValueResolve.class);
		if (bean == null) {
			{
				final AutoFillColumnValueResolve resolveBean = this.creatorAutoColumnBean();
				beanFactory.registerSingleton("mybatis.autofill." + resolveBean.columnName(), resolveBean);

				System.out.println("register inner default ValueResolve[creator]");
			}

			{
				final AutoFillColumnValueResolve resolveBean = this.updaterAutoColumnBean();
				beanFactory.registerSingleton("mybatis.autofill." + resolveBean.columnName(), resolveBean);

				System.out.println("register inner default ValueResolve[updater]");
			}
		}
	}

	private void registerMybatisInterceptorBean(final ConfigurableListableBeanFactory beanFactory) {
		final AutoFillUpdateInterceptor bean = beanFactory.getBean(AutoFillUpdateInterceptor.class);
		if (bean == null) {
			final AutoFillUpdateInterceptor resolveBean = this.register();
			beanFactory.registerSingleton(resolveBean.getClass().getSimpleName(), resolveBean);
		}
	}

	@Override
	public void onApplicationEvent(final ContextRefreshedEvent event) {

		final ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) event.getApplicationContext().getAutowireCapableBeanFactory();

		this.registerMybatisInterceptorBean(beanFactory);
		this.registerValueResolveBean(beanFactory);

		final Map<String, AutoFillColumnValueResolve> map = event.getApplicationContext().getBeansOfType(AutoFillColumnValueResolve.class);

		for (final AutoFillColumnValueResolve r : map.values()) {
			final SqlCommandType[] sctList = r.supporSqlType();
			if (sctList != null) {
				for (final SqlCommandType sct : sctList) {
					List<AutoFillColumnValueResolve> list;
					if (sct != null) {
						if (!COL_RESOLVE_LIST.containsKey(sct)) {
							list = new ArrayList<AutoFillColumnValueResolve>();
							COL_RESOLVE_LIST.put(sct, list);
						} else {
							list = COL_RESOLVE_LIST.get(sct);
						}
						list.add(r);
					}
				}
			}
		}

	}
}

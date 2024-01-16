package org.orztoys.jdbc.strictconn;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;


public class JdbcStrictConnConfiguration implements ApplicationListener<ContextRefreshedEvent>, BeanPostProcessor {

	private final static StrictConnBuilder connBuilder;

	static {
		connBuilder = StrictConnBuilderFactory.builder();
		if (connBuilder == null) {
			throw new ExceptionInInitializerError("unsupport the jdbc driver");
		}
	}

	// print mysql db timezone in current session
	public void printDbConfig(DataSource ds) {
		StringWriter dbConfig = new StringWriter();

		try (PrintWriter dbConfigPrint = new PrintWriter(dbConfig); Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
			dbConfigPrint.print("db config =\t");
			try (ResultSet rs = st.executeQuery(connBuilder.printConfSql())) {
				while (rs.next()) {
					StringBuffer line = new StringBuffer();
					line.append(rs.getString(1)).append("=").append(rs.getString(2));
					dbConfigPrint.print(line + ", ");
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		System.out.println(dbConfig.toString());
	}

	private static int getConnStrictScheme() {

		String scheme = System.getProperty("jdbc.strictconn.schemes", "");
		int sch = StrictConnSchemeEnum.merge(StrictConnSchemeEnum.separate(scheme));

		if (sch == 0) {
			sch = StrictConnSchemeEnum.merge(StrictConnSchemeEnum.TIMEZONE);
		}

		return sch;
	}

	@Override
	public void onApplicationEvent(ContextRefreshedEvent event) {
		DataSource ds = event.getApplicationContext().getBean(DataSource.class);
		this.printDbConfig(ds);
	}

	public static JdbcStrictConn getDatabaseStrictConn(int scheme) {
		return new JdbcStrictConn(connBuilder.buildScheme(scheme));
	}

	private void strictConnURL(org.springframework.boot.autoconfigure.jdbc.DataSourceProperties properties) {

		String url = properties.getUrl();
		url = getDatabaseStrictConn(getConnStrictScheme()).make(url);
		properties.setUrl(url);

	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof org.springframework.boot.autoconfigure.jdbc.DataSourceProperties) {
			strictConnURL((org.springframework.boot.autoconfigure.jdbc.DataSourceProperties) bean);
		}
		return bean;
	}

}

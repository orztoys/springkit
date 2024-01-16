package org.orztoys.jdbc.strictconn;

import org.springframework.beans.factory.FactoryBean;

/**
 * use in spring4
 */
public class JdbcStrictConnWrapper implements FactoryBean<String> {

	private String dbUrl;
	private String scheme;

	public void setJdbcURL(String dbUrl) {
		this.dbUrl = dbUrl;
	}

	public void setSchemes(String scheme) {
		this.scheme = scheme;
	}

	@Override
	public String getObject() throws Exception {

		int sch = StrictConnSchemeEnum.merge(StrictConnSchemeEnum.separate(scheme));

		if (sch == 0) {
			sch = StrictConnSchemeEnum.merge(StrictConnSchemeEnum.TIMEZONE);
		}

		return JdbcStrictConnConfiguration.getDatabaseStrictConn(sch).make(dbUrl);
	}

	@Override
	public Class<String> getObjectType() {
		return String.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}

}

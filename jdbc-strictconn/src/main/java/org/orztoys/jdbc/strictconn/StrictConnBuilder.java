package org.orztoys.jdbc.strictconn;

import org.orztoys.jdbc.strictconn.JdbcStrictConnSchemeResolve.StrictConnParamDefinition;

interface StrictConnBuilder {

	public StrictConnParamDefinition[] buildScheme(int connScheme);

	public String printConfSql();

}

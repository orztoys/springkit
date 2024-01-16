package org.orztoys.mybatis.autofill;

import org.apache.ibatis.mapping.SqlCommandType;

/**
 * the column has support what SQL type( insert or update)
 */
public interface AutoFillColumnValueResolve {
	/**
	 * fill value
	 * @param sct
	 * @param table
	 * @return
	 */
	public Object resolve(SqlCommandType sct, String table);

	/**
	 * support the type and table
	 * @param sct
	 * @param table
	 * @return
	 */
	public boolean support(SqlCommandType sct, String table);

	/**
	 * define support what SQL type
	 * 
	 * @return
	 */
	public SqlCommandType[] supporSqlType();

	/**
	 * which column can be auto fill (no define in code in anything)
	 * @return
	 */
	public String columnName();
}

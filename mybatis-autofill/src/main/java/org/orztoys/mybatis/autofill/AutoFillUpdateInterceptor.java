package org.orztoys.mybatis.autofill;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.springframework.beans.factory.DisposableBean;

@Intercepts({ @Signature(type = StatementHandler.class, method = "prepare", args = { Connection.class, Integer.class }) })
class AutoFillUpdateInterceptor implements Interceptor, DisposableBean {

	private final static boolean DEBUG = false;

	@Override
	public void destroy() throws Exception {
		AUTOFILL_TB_CACHE.clear();
	}

	@Override
	protected void finalize() {
		try {
			this.destroy();
		} catch (Exception e) {
		}
	}

	/**
	 * 表存在自动添加字段标记
	 */
	private final static ConcurrentMap<String, Boolean> AUTOFILL_TB_CACHE = new ConcurrentHashMap<>();

	private final org.apache.ibatis.logging.Log logger = org.apache.ibatis.logging.LogFactory.getLog(AutoFillUpdateInterceptor.class);

	public AutoFillUpdateInterceptor() {

	}

	private SqlCommandType getSqlType(String sql) {
		Pattern p1 = Pattern.compile("^\\s*INSERT\\s+", Pattern.CASE_INSENSITIVE);
		Pattern p2 = Pattern.compile("^\\s*UPDATE\\s+", Pattern.CASE_INSENSITIVE);
		if (p1.matcher(sql).find()) {
			return SqlCommandType.INSERT;
		} else if (p2.matcher(sql).find()) {
			return SqlCommandType.UPDATE;
		} else {
			return null;
		}
	}

	@Override
	public Object intercept(Invocation invocation) throws Throwable {

		Connection connection = (Connection) invocation.getArgs()[0];
		StatementHandler statementHandler = (StatementHandler) invocation.getTarget();
		BoundSql boundSql = statementHandler.getBoundSql();
		String sql = boundSql.getSql();

		SqlCommandType sct = getSqlType(sql);

		TableToken tbToken = this.extractTable(sql, sct);

		if (tbToken != null && sct != null) {

			String newSql = sql;

			List<AutoFillColumnValueResolve> rlist = AutoFillColumnBootstrap.COL_RESOLVE_LIST.get(sct);
			if (rlist != null && !rlist.isEmpty()) {

				for (AutoFillColumnValueResolve r : rlist) {
					if (r.support(sct, tbToken.table)) {
						String debugLog = String.format("%s.%s try add", tbToken.table, r.columnName());
						logger.debug(debugLog);

						Object v = r.resolve(sct, tbToken.table);
						if (v != null) {

							String tailSql = extractDiscludeTbnSql(tbToken, sql);

							if (this.includeColumn(tailSql, r.columnName())) {
								break;
							} else if (!this.testColumn(connection, tbToken.table, r.columnName())) {
								break;
							} else {
								if (sct == SqlCommandType.INSERT) {
									newSql = this.attachAutoColumnWhenInsert(tbToken, sql, r.columnName(), v);
								} else if (sct == SqlCommandType.UPDATE) {
									newSql = this.attachAutoColumnWhenUpdate(tbToken, sql, r.columnName(), v);
								}
							}
						}

					}
				}
			}

			if (newSql != null && !Objects.equals(sql, newSql)) {
				Field field = boundSql.getClass().getDeclaredField("sql");
				boolean access = field.isAccessible();
				try {
					field.setAccessible(true);
					field.set(boundSql, newSql);
				} finally {
					field.setAccessible(access);
				}
			}
		}

		return invocation.proceed();
	}

	@Override
	public Object plugin(Object target) {
		if (target instanceof StatementHandler) {
			return Plugin.wrap(target, this);
		} else {
			return target;
		}
	}

	@Override
	public void setProperties(Properties properties) {
	}

	private static class TableToken {
		private String table;
		private int start;
		private int end;
	}

	private TableToken extractTable(String sql, SqlCommandType sct) {

		if (sct == SqlCommandType.INSERT) {
			Pattern p = Pattern.compile("^\\s*INSERT\\s+INTO\\s+([^\\(\\s]+)(?:\\s+|\\()", Pattern.CASE_INSENSITIVE);

			Matcher match = p.matcher(sql);
			if (match.find()) {
				TableToken token = new TableToken();
				token.table = match.group(1);
				token.start = match.start(1);
				token.end = match.end(1);
				return token;
			} else {
				p = Pattern.compile("^\\s*INSERT\\s+IGNORE\\s+INTO\\s+([^\\(\\s]+)(?:\\s+|\\()", Pattern.CASE_INSENSITIVE);
				match = p.matcher(sql);
				if (match.find()) {
					TableToken token = new TableToken();
					token.table = match.group(1);
					token.start = match.start(1);
					token.end = match.end(1);
					return token;
				}
			}
		} else if (sct == SqlCommandType.UPDATE) {
			Pattern p = Pattern.compile("^\\s*UPDATE\\s+([^\\(\\s]+)(?:\\s+SET\\s+)", Pattern.CASE_INSENSITIVE);
			Matcher match = p.matcher(sql);
			if (match.find()) {
				TableToken token = new TableToken();
				token.table = match.group(1);
				token.start = match.start(1);
				token.end = match.end(1);
				return token;
			}
		}

		return null;
	}

	private String extractDiscludeTbnSql(TableToken tbToken, final String sql) {
		return sql.substring(tbToken.end + 1);
	}

	private boolean includeColumn(String sqlNoIncludeTb, String column) {
		boolean match = Pattern.compile("\\b" + column + "\\b", Pattern.CASE_INSENSITIVE).matcher(sqlNoIncludeTb).find();
		return match;
	}

	private boolean testColumn(final Connection connection, String table, final String column) {

		boolean tbHasThisCol = false;

		String catalog = null;
		{
			String[] parts = table.split("\\.");
			if (parts.length == 1) {
			} else {
				catalog = parts[0];
				table = parts[1];
			}
			catalog = catalog == null ? null : catalog.replaceAll("`", "");
			table = table == null ? null : table.replaceAll("`", "");
		}

		String TB_CKEY = ((catalog == null ? "" : catalog + ".") + table + "." + column).toLowerCase();

		if (Boolean.TRUE.equals(AUTOFILL_TB_CACHE.get(TB_CKEY))) {
			tbHasThisCol = true;
		} else if (connection != null) {

			if (DEBUG) {
				try (ResultSet tbDescRs = connection.getMetaData().getColumns(null, catalog, table, null)) {

					StringWriter tbDesc = new StringWriter();
					PrintWriter pw = new PrintWriter(tbDesc);

					while (tbDescRs.next()) {
						String colN = tbDescRs.getString("COLUMN_NAME");
						if (column.equalsIgnoreCase(colN)) {
							tbHasThisCol = true;
						}

						pw.print(colN);
						pw.print(", ");
					}
					String tb = table;

					logger.debug("desc " + tb + "; --> (" + tbDesc.toString() + ")");

				} catch (SQLException e) {
					logger.error(e.getMessage(), e);
				}
			} else {
				try (ResultSet tbDescRs = connection.getMetaData().getColumns(null, catalog, table, column)) {
					tbHasThisCol = tbDescRs.next();
				} catch (SQLException e) {
					logger.error(e.getMessage(), e);
				}
			}
			if (tbHasThisCol) {
				AUTOFILL_TB_CACHE.putIfAbsent(TB_CKEY, true);
			}
		}

		return tbHasThisCol;
	}

	private String attachAutoColumnWhenUpdate(TableToken tbToken, final String sql, final String newColumn, final Object newColVal) {
		StringBuffer newSql = new StringBuffer();

		Matcher setTokenMatch;
		{
			Pattern p = Pattern.compile("SET\\s+", Pattern.CASE_INSENSITIVE);
			setTokenMatch = p.matcher(sql);
		}
		if (setTokenMatch.find()) {
			int setTokenTail = setTokenMatch.end();
			newSql.append(sql.substring(0, setTokenTail));
			newSql.append(" ").append(newColumn).append("=");

			String autoColValSql;
			if (newColVal instanceof String) {
				autoColValSql = "'" + newColVal + "'";
			} else {
				autoColValSql = String.valueOf(newColVal);
			}
			newSql.append(autoColValSql).append(", ");

			newSql.append(sql.substring(setTokenTail));

			return newSql.toString();
		} else {
			return null;
		}

	}

	private String attachAutoColumnWhenInsert(TableToken tbToken, final String sql, final String newColumn, final Object newColVal) {
		StringBuffer newSql = new StringBuffer();
		int start = tbToken.start;
		if (tbToken.start > 0) {
			newSql.append(sql, 0, start);
			String tbn = tbToken.table;
			newSql.append(tbn);
		}

		String noTbnSql = extractDiscludeTbnSql(tbToken, sql);

		noTbnSql = noTbnSql.replaceFirst("\\(", "(" + newColumn + ", ");

		Matcher valTokenMatch;
		{
			Pattern p = Pattern.compile("VALUES\\s*\\(", Pattern.CASE_INSENSITIVE);
			valTokenMatch = p.matcher(noTbnSql);
		}
		if (valTokenMatch.find()) {
			int valTokenTail = valTokenMatch.end();
			String origInsVal = noTbnSql.substring(valTokenTail);
			noTbnSql = noTbnSql.substring(0, valTokenTail);

			String autoColValSql;
			if (newColVal instanceof String) {
				autoColValSql = "'" + newColVal + "'";
			} else {
				autoColValSql = String.valueOf(newColVal);
			}

			noTbnSql = noTbnSql + autoColValSql + ", " + origInsVal;

			newSql.append(noTbnSql);

			return newSql.toString();
		} else {
			return null;
		}

	}

	

	

}

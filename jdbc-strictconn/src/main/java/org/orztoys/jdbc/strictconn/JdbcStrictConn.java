package org.orztoys.jdbc.strictconn;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.orztoys.jdbc.strictconn.JdbcStrictConnSchemeResolve.StrictConnParamDefinition;

class JdbcStrictConn {

	private final StrictConnParamDefinition[] strictConnParams;

	public JdbcStrictConn(StrictConnParamDefinition[] strictConnParams) {
		this.strictConnParams = strictConnParams;
	}

	public String make(String dburl) {

		StringBuffer newUrl = new StringBuffer();

		newUrl.append(buildURI(dburl));

		String query = buildQuery(dburl);
		query = query == null ? "" : query.trim();

		ConnQueryMaker maker = new ConnQueryMaker(query);

		for (StrictConnParamDefinition connParam : strictConnParams) {
			if (maker.substitute(connParam)) {
				maker.doSubstitute(connParam);
			}
		}

		String newQuery = maker.outputQuery();

		if (newQuery.length() > 0) {
			newUrl.append("?").append(newQuery);
		}

		if (!dburl.equals(newUrl.toString())) {
			System.out.println(String.format("change dburl %s --> %s", dburl, newUrl.toString()));
		}

		return newUrl.toString();
	}

	private static class ConnQueryMaker {
		private final StringBuffer query;

		public ConnQueryMaker(String query) {
			this.query = new StringBuffer(query);
		}

		public String outputQuery() {
			return query.toString();
		}

		public boolean substitute(StrictConnParamDefinition strictConnParam) {

			Matcher match = buildMatchWithQuery(strictConnParam.name);

			if (match.find()) {
				String originalName = match.group(1);
				String originalVal = match.group(2);
				try {
					// the name not same
					if (!strictConnParam.name.equals(originalName)) {
						return true;
					}
					String newVal = URLEncoder.encode(strictConnParam.valueResolve.resolve(originalVal), StandardCharsets.UTF_8.name());
					return Objects.equals(newVal, originalVal);
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException("UTF_8 is not support");
				}
			} else {
				return true;
			}
		}

		public void doSubstitute(StrictConnParamDefinition strictConnParam) {
			Matcher match = buildMatchWithQuery(strictConnParam.name);

			String encVal;

			if (match.find()) {
				try {
					encVal = URLEncoder.encode(strictConnParam.valueResolve.resolve(match.group(2)), StandardCharsets.UTF_8.name());
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException("UTF_8 is not support");
				}

				String originalName = match.group(1);
				String originalVal = match.group(2);
				if (strictConnParam.override && !Objects.equals(originalVal, encVal)) {
					String newQuery = replace(this.query.toString(), match, strictConnParam.name, encVal);
					this.query.delete(0, this.query.length());
					this.query.append(newQuery);
				} else if (!strictConnParam.name.equals(originalName)) {
					String newQuery = replace(this.query.toString(), match, strictConnParam.name, originalVal);
					this.query.delete(0, this.query.length());
					this.query.append(newQuery);
				}
			} else {
				try {
					encVal = URLEncoder.encode(strictConnParam.valueResolve.resolve(null), StandardCharsets.UTF_8.name());
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException("UTF_8 is not support");
				}
				if (query.length() > 0) {
					query.append("&");
				}
				query.append(strictConnParam.name).append("=").append(encVal);
			}
		}

		private Matcher buildMatchWithQuery(String name) {
			Pattern p = Pattern.compile("(?:&|^)(" + name + ")\\s*=(.*?)(?:&|$)", Pattern.CASE_INSENSITIVE);
			Matcher match = p.matcher(query);

			return match;
		}

		private String replace(final String query, Matcher match, String name, String value) {

			int s1 = match.toMatchResult().start(1);
			int e1 = match.toMatchResult().end(1);
			int s2 = match.toMatchResult().start(2);
			int e2 = match.toMatchResult().end(2);

			return query.substring(0, s1) + name + query.substring(e1, s2) + value + query.substring(e2);

		}

	}

	private static String buildURI(String dburl) {
		if (dburl == null || dburl.trim().length() == 0) {
			return "";
		}

		int idx = dburl.indexOf("?");
		if (idx >= 0 && idx + 1 < dburl.length()) {
			return dburl.substring(0, idx);

		} else {
			return dburl;
		}
	}

	private static String buildQuery(String dburl) {

		if (dburl == null || dburl.trim().length() == 0) {
			return "";
		}

		int idx = dburl.indexOf("?");

		if (idx >= 0 && idx + 1 < dburl.length()) {
			String query = dburl.substring(idx + 1);

			return query;
		} else {
			return "";
		}
	}

}

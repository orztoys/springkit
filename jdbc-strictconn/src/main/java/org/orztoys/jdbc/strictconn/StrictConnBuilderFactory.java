package org.orztoys.jdbc.strictconn;

class StrictConnBuilderFactory {

	public static StrictConnBuilder builder() {
		return mysqlDriverStrictConn();
	}

	private static StrictConnBuilder mysqlDriverStrictConn() {
		Integer ver = null;

		try {
			try {
				// 8.x
				Class<?> clzz = Thread.currentThread().getContextClassLoader().loadClass("com.mysql.cj.Constants");
				if (clzz != null) {
					ver = 8;

					return new Mysql8StrictConnBuilder();
				}
			} catch (ClassNotFoundException | IllegalArgumentException | SecurityException e) {
			}

			try {
				// 6.x
				Class<?> clzz = Thread.currentThread().getContextClassLoader().loadClass("com.mysql.cj.core.Constants");
				if (clzz != null) {
					ver = 6;

					return new Mysql6StrictConnBuilder();
				}
			} catch (ClassNotFoundException | IllegalArgumentException | SecurityException e) {
			}

			try {
				// 5.x
				Class<?> clzz = Thread.currentThread().getContextClassLoader().loadClass("com.mysql.jdbc.Constants");
				if (clzz != null) {
					ver = 5;

					return new Mysql5StrictConnBuilder();
				}
			} catch (ClassNotFoundException | IllegalArgumentException | SecurityException e) {
			}

			return null;
		} finally {
			if (ver != null) {
				System.out.println("mysql-conntector-java.verion = " + ver);
			}
		}

	}

}

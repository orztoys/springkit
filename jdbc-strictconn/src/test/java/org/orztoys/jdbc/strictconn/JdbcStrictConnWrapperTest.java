package org.orztoys.jdbc.strictconn;

import java.util.Objects;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;

@RunWith(BlockJUnit4ClassRunner.class)
public class JdbcStrictConnWrapperTest {

	@Test
	public void testSpring4() throws Exception {
		JdbcStrictConnWrapper wrapper = new JdbcStrictConnWrapper();
		wrapper.setSchemes("TIMEZONE,CHARSET,TIMEOUT");
		wrapper.setJdbcURL("jdbc:mysql://localhost:3306/test?useunicode=true&servertimezone=GMT%2B09%3A00");

		String dbUrl = wrapper.getObject();

		String assertURL = "jdbc:mysql://localhost:3306/test?useunicode=true&serverTimezone=GMT%2B09%3A00&characterEncoding=UTF-8&autoReconnect=true&autoReconnectForPools=true&maxReconnects=3&initialTimeout=5&failOverReadOnly=false&connectTimeout=3000&socketTimeout=3000";

		Assert.assertTrue(dbUrl, Objects.equals(dbUrl, assertURL));
	}

}

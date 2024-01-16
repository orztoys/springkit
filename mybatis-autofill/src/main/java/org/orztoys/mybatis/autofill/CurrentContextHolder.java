package org.orztoys.mybatis.autofill;

public class CurrentContextHolder {

	final static ThreadLocal<String> CURRENT = new ThreadLocal<String>();

	public String get() {
		return CURRENT.get();
	}

	public void clear() {
		CURRENT.remove();
	}

}

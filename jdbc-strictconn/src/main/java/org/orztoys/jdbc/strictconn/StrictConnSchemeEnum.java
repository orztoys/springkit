package org.orztoys.jdbc.strictconn;

import java.util.ArrayList;
import java.util.List;

public enum StrictConnSchemeEnum {

	/**
	 * set time zone use by machine
	 */
	TIMEZONE(0x00000001),

	/**
	 * set charset = utf8
	 */
	CHARSET(0x00000002),

	/**
	 * set conn params to optimized scheme
	 */
	OPTIMIZE_CONN(0x00000003),

	/**
	 * set conn timeout to optimized scheme
	 */
	TIMEOUT(0x00000004);

	public final int value;

	private StrictConnSchemeEnum(int value) {
		this.value = value;
	}

	public final static StrictConnSchemeEnum nameOf(String name) {
		for (StrictConnSchemeEnum e : values()) {
			if (e.name().equalsIgnoreCase(name)) {
				return e;
			}
		}

		return null;
	}

	public final static StrictConnSchemeEnum[] separate(int scheme) {

		List<StrictConnSchemeEnum> list = new ArrayList<>();

		for (StrictConnSchemeEnum e : values()) {
			if ((e.value & scheme) == e.value) {
				list.add(e);
			}
		}

		return list.toArray(new StrictConnSchemeEnum[list.size()]);
	}

	public final static StrictConnSchemeEnum[] separate(String scheme) {

		if (scheme == null) {
			return new StrictConnSchemeEnum[0];
		}

		List<StrictConnSchemeEnum> list = new ArrayList<>();
		for (String s : scheme.split(",")) {
			StrictConnSchemeEnum e = nameOf(s);
			if (e != null) {
				list.add(e);
			}
		}
		return list.toArray(new StrictConnSchemeEnum[list.size()]);
	}

	public final static int merge(StrictConnSchemeEnum... schemes) {

		int sch = 0x00;
		if (schemes != null) {
			for (StrictConnSchemeEnum s : schemes) {
				sch = sch | s.value;
			}
		}

		return sch;

	}
}

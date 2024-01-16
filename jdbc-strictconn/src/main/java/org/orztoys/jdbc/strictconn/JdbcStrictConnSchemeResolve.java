package org.orztoys.jdbc.strictconn;

interface JdbcStrictConnSchemeResolve {

	public String showConnSql();

	public StrictConnParamDefinition[] strictConnParamDefinitions();

	public static class StrictConnParamDefinition {

		final String name;
		final boolean override;
		final ParamValueResolve valueResolve;

		public StrictConnParamDefinition(String name, ParamValueResolve valueResolve, boolean override) {
			this.name = name;
			this.valueResolve = valueResolve;
			this.override = override;
		}

		@Override
		public String toString() {
			return name;
		}
	}

	public interface ParamValueResolve {
		public String resolve(String original);
	}

	public static class FixedParamValueResolve implements ParamValueResolve {
		private final String value;

		public FixedParamValueResolve(String value) {
			this.value = value;
		}

		@Override
		public String resolve(String original) {
			return value;
		}

	}

}

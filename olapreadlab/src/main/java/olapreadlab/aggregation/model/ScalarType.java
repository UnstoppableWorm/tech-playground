package olapreadlab.aggregation.model;

public enum ScalarType {
	DECIMAL {
		@Override public Object convert(Object value) {
			if (value instanceof java.math.BigDecimal decimal) return decimal;
			return new java.math.BigDecimal(value.toString());
		}
	},
	LONG {
		@Override public Object convert(Object value) {
			if (value instanceof Number number) return number.longValue();
			return Long.valueOf(value.toString());
		}
	},
	INTEGER {
		@Override public Object convert(Object value) {
			if (value instanceof Number number) return number.intValue();
			return Integer.valueOf(value.toString());
		}
	},
	STRING {
		@Override public Object convert(Object value) {
			return value.toString();
		}
	};

	public abstract Object convert(Object value);
}

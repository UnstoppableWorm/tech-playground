package olapreadlab.aggregation.adapter.jdbc.mapping;

import java.util.regex.Pattern;

public record SqlIdentifier(String value) {

	private static final Pattern SAFE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");

	public SqlIdentifier {
		if (value == null || !SAFE.matcher(value).matches()) {
			throw new IllegalArgumentException("Unsafe SQL identifier in storage binding: " + value);
		}
	}

	public static SqlIdentifier of(String value) {
		return new SqlIdentifier(value);
	}
}

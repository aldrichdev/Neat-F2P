package com.openrsc.server.util.rsc;

public class IntegerUtil {
	public static Integer convertLongToInteger(Long value) {
		if (value == null) {
			return null; // Handle null input
		}

		if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
			throw new ArithmeticException("Value out of int range: " + value);
		}

		return value.intValue(); // Safe conversion
	}
}
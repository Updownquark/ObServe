package org.observe.entity.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.observe.entity.jdbc.JdbcEntitySupport.JdbcColumn.BooleanColumnType;
import org.observe.util.TypeTokens;
import org.qommons.StringUtils;
import org.qommons.io.Format;

import com.google.common.reflect.TypeToken;

public class JdbcEntitySupport {
	public interface JdbcTypeSupport<T> {
		<T2 extends T> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support);

		String getDefaultColumnType(TypeToken<? extends T> fieldType);

		public static final JdbcTypeSupport<Integer> INT = new JdbcTypeSupport<Integer>() {
			@Override
			public <T2 extends Integer> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "INT":
				case "INTEGER":
				case "BIGINT":
					return (JdbcColumn<T2>) JdbcColumn.INT;
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Integer> fieldType) {
				return "INT";
			}
		};

		public static final JdbcTypeSupport<Long> LONG = new JdbcTypeSupport<Long>() {
			@Override
			public <T2 extends Long> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "INT":
				case "INTEGER":
				case "BIGINT":
					return (JdbcColumn<T2>) JdbcColumn.LONG;
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Long> fieldType) {
				return "BIGINT";
			}
		};

		public static final JdbcTypeSupport<Double> DOUBLE = new JdbcTypeSupport<Double>() {
			@Override
			public <T2 extends Double> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "DOUBLE":
				case "FLOAT":
				case "NUMERIC":
					return (JdbcColumn<T2>) JdbcColumn.DOUBLE;
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Double> fieldType) {
				return "DOUBLE";
			}
		};

		public static final JdbcTypeSupport<String> STRING = new JdbcTypeSupport<String>() {
			@Override
			public <T2 extends String> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "CHAR":
				case "VARCHAR":
				case "VARCHAR2":
				case "TEXT":
				case "TINYTEXT":
				case "MEDIUMTEXT":
				case "LONGTEXT":
				case "CLOB":
					return (JdbcColumn<T2>) JdbcColumn.STRING;
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends String> fieldType) {
				return "VARCHAR";
			}
		};

		public static final JdbcTypeSupport<Boolean> BOOLEAN = new JdbcTypeSupport<Boolean>() {
			@Override
			public <T2 extends Boolean> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "BOOL":
				case "BOOLEAN":
					return (JdbcColumn<T2>) new JdbcColumn.BooleanColumn(!type.isPrimitive(), BooleanColumnType.BOOLEAN);
				case "CHAR":
				case "NCHAR":
				case "VARCHAR":
				case "VARCHAR2":
				case "NVARCHAR":
				case "BINARY":
				case "VARBINARY":
				case "TEXT":
				case "NTEXT":
				case "TINYTEXT":
				case "MEDIUMTEXT":
				case "LONGTEXT":
				case "CLOB":
					return (JdbcColumn<T2>) new JdbcColumn.BooleanColumn(!type.isPrimitive(), BooleanColumnType.CHAR);
				case "BIT":
				case "INT":
				case "INTEGER":
				case "TINYINT":
				case "MEDINUMINT":
				case "BIGINT":
					return (JdbcColumn<T2>) new JdbcColumn.BooleanColumn(!type.isPrimitive(), BooleanColumnType.NUMBER);
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Boolean> fieldType) {
				return "BOOLEAN";
			}
		};

		public static final JdbcTypeSupport<Instant> INSTANT = new JdbcTypeSupport<Instant>() {
			@Override
			public <T2 extends Instant> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "TIMESTAMP":
				case "DATETIME":
					return (JdbcColumn<T2>) JdbcColumn.INSTANT;
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Instant> fieldType) {
				return "TIMESTAMP";
			}
		};

		public static final JdbcTypeSupport<Duration> DURATION = new JdbcTypeSupport<Duration>() {
			@Override
			public <T2 extends Duration> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName, JdbcEntitySupport support) {
				switch (columnTypeName.toUpperCase()) {
				case "INT":
				case "INTEGER":
				case "BIGINT":
					return (JdbcColumn<T2>) JdbcColumn.DURATION_MILLIS;
				case "FLOAT":
				case "DOUBLE":
				case "DOUBLE PRECISION":
				case "REAL":
				case "DECIMAL":
				case "NUMERIC":
					return (JdbcColumn<T2>) JdbcColumn.DURATION_SECONDS;
				default:
					return null;
				}
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Duration> fieldType) {
				return "DOUBLE";
			}
		};

		@Deprecated
		public static final JdbcTypeSupport<Collection<?>> COLLECTION = new JdbcTypeSupport<Collection<?>>() {
			@Override
			public <T2 extends Collection<?>> JdbcColumn<T2> columnFor(TypeToken<T2> type, String columnTypeName,
				JdbcEntitySupport support) {
				if (!isStringType(columnTypeName))
					return null;
				Class<?> valueType = TypeTokens.get()
					.unwrap(TypeTokens.getRawType(type.resolveType(Collection.class.getTypeParameters()[0])));
				BiConsumer<StringBuilder, Object> format;
				Function<String, Object> parser;
				if (valueType == String.class) {
					format = StringBuilder::append;
					parser = v -> v;
				} else if (valueType == int.class) {
					format = StringBuilder::append;
					parser = Integer::parseInt;
				} else if (valueType == long.class) {
					format = StringBuilder::append;
					parser = Long::parseLong;
				} else if (valueType == double.class) {
					format = StringBuilder::append;
					DecimalFormat df = new DecimalFormat("0.#");
					parser = s -> {
						try {
							return Format.parseDouble(s, df);
						} catch (ParseException e) {
							throw new IllegalArgumentException(e);
						}
					};
				} else if (valueType == float.class) {
					format = StringBuilder::append;
					DecimalFormat df = new DecimalFormat("0.#");
					parser = s -> {
						try {
							return (float) Format.parseDouble(s, df);
						} catch (ParseException e) {
							throw new IllegalArgumentException(e);
						}
					};
				} else if (valueType == boolean.class) {
					format = StringBuilder::append;
					parser = Boolean::valueOf;
				} else if (valueType == Duration.class) {
					format = (str, d) -> Format.DURATION.append(str, (Duration) d);
					parser = s -> {
						try {
							return Format.DURATION.parse(s);
						} catch (ParseException e) {
							throw new IllegalArgumentException(e);
						}
					};
				} else
					return null;
				return new JdbcColumn<T2>() {
					@Override
					public StringBuilder serialize(T2 value, StringBuilder str) {
						format.accept(str, value);
						return str;
					}

					@Override
					public T2 deserialize(ResultSet rs, int column) throws SQLException {
						String str = rs.getString(column);
						List<Object> values = new ArrayList<>();
						int start = 0;
						boolean escaped = false;
						for (int c = 0; c < str.length(); c++) {
							if (escaped) {//
							} else if (str.charAt(c) == '\\')
								escaped = true;
							else if (str.charAt(c) == ',') {
								values.add(parser.apply(str.substring(0, c)));
								start = c + 1;
							}
						}
						if (start < str.length())
							values.add(parser.apply(str.substring(0, str.length())));
						return (T2) values;
					}

					@Override
					public String getTypeName() {
						return "CLOB";
					}

					@Override
					public boolean testsAdjacent() {
						return false;
					}

					@Override
					public boolean isAdjacent(T2 v1, T2 v2) {
						return false;
					}
				};
			}

			@Override
			public String getDefaultColumnType(TypeToken<? extends Collection<?>> fieldType) {
				return "VARCHAR";
			}
		};
	}

	static boolean isStringType(String columnTypeName) {
		switch (columnTypeName) {
		case "CHAR":
		case "NCHAR":
		case "VARCHAR":
		case "VARCHAR2":
		case "NVARCHAR":
		case "BINARY":
		case "VARBINARY":
		case "TEXT":
		case "NTEXT":
		case "TINYTEXT":
		case "MEDIUMTEXT":
		case "LONGTEXT":
		case "CLOB":
			return true;
		default:
			return false;
		}
	}

	public interface JdbcColumn<T> {
		StringBuilder serialize(T value, StringBuilder str);

		T deserialize(ResultSet rs, int column) throws SQLException;

		String getTypeName();

		boolean testsAdjacent();

		boolean isAdjacent(T v1, T v2);

		public static final JdbcColumn<Integer> INT = new JdbcColumn<Integer>() {
			@Override
			public StringBuilder serialize(Integer value, StringBuilder str) {
				return str.append(value == null ? "NULL" : value.toString());
			}

			@Override
			public Integer deserialize(ResultSet rs, int column) throws SQLException {
				Object value = rs.getObject(column);
				if (value == null)
					return null;
				else if (value instanceof Integer)
					return (Integer) value;
				else
					return ((Number) value).intValue();
			}

			@Override
			public String getTypeName() {
				return "INTEGER";
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(Integer v1, Integer v2) {
				return v1 != null && v2 != null && v1 == v2;
			}
		};

		public static final JdbcColumn<Long> LONG = new JdbcColumn<Long>() {
			@Override
			public StringBuilder serialize(Long value, StringBuilder str) {
				return str.append(value == null ? "NULL" : value.toString());
			}

			@Override
			public Long deserialize(ResultSet rs, int column) throws SQLException {
				long res = rs.getLong(column);
				if (rs.wasNull())
					return null;
				return res;
			}

			@Override
			public String getTypeName() {
				return "BIGINT";
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(Long v1, Long v2) {
				return v1 != null && v2 != null && v1 == v2;
			}
		};
		public static final JdbcColumn<Double> DOUBLE = new JdbcColumn<Double>() {
			@Override
			public StringBuilder serialize(Double value, StringBuilder str) {
				return str.append(value == null ? "NULL" : value.toString());
			}

			@Override
			public Double deserialize(ResultSet rs, int column) throws SQLException {
				double res = rs.getDouble(column);
				if (rs.wasNull())
					return null;
				return res;
			}

			@Override
			public String getTypeName() {
				return "DOUBLE";
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(Double v1, Double v2) {
				return v1 != null && v2 != null && v1 == v2;
			}
		};

		public static final JdbcColumn<String> STRING = new JdbcColumn<String>() {
			@Override
			public StringBuilder serialize(String value, StringBuilder str) {
				str.append('\'');
				for (int c = 0; c < value.length(); c++) {
					if (value.charAt(c) == '\'')
						str.append('\'');
					str.append(value.charAt(c));
				}
				str.append('\'');
				return str;
			}

			@Override
			public String deserialize(ResultSet rs, int column) throws SQLException {
				return rs.getString(column);
			}

			@Override
			public String getTypeName() {
				return "VARCHAR";
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(String v1, String v2) {
				if (v1 == null || v2 == null || v1.length() != v2.length())
					return false;
				for (int c = 0; c < v1.length() - 1; c++)
					if (v1.charAt(c) != v2.charAt(c))
						return false;
				return v2.charAt(v1.length() - 1) == v1.charAt(v1.length() - 1) + 1;
			}
		};

		public enum BooleanColumnType {
			BOOLEAN, NUMBER, CHAR;
		}

		public static class BooleanColumn implements JdbcColumn<Boolean> {
			private final boolean isNullable;
			private final BooleanColumnType theType;

			public BooleanColumn(boolean nullable, BooleanColumnType type) {
				isNullable = nullable;
				theType = type;
			}

			@Override
			public StringBuilder serialize(Boolean value, StringBuilder str) {
				if (isNullable && value == null)
					str.append("NULL");
				else if (Boolean.TRUE.equals(value)) {
					switch (theType) {
					case BOOLEAN:
						str.append("TRUE");
						break;
					case NUMBER:
						str.append('1');
						break;
					case CHAR:
						str.append("'T'");
						break;
					}
				} else {
					switch (theType) {
					case BOOLEAN:
						str.append("FALSE");
						break;
					case NUMBER:
						str.append('0');
						break;
					case CHAR:
						str.append("'F'");
						break;
					}
				}
				return str;
			}

			@Override
			public Boolean deserialize(ResultSet rs, int column) throws SQLException {
				boolean res = rs.getBoolean(column);
				if (rs.wasNull())
					return null;
				return res;
			}

			@Override
			public String getTypeName() {
				return "BOOLEAN";
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(Boolean v1, Boolean v2) {
				return true;
			}
		}

		public static final JdbcColumn<Instant> INSTANT = new JdbcColumn<Instant>() {
			private final SimpleDateFormat FORMAT = new SimpleDateFormat("YYYY-MM-DD HH:mm:ss");

			@Override
			public StringBuilder serialize(Instant value, StringBuilder str) {
				if (value == null) {
					str.append("NULL");
					return str;
				}
				str.append('\'').append(FORMAT.format(new Date(value.toEpochMilli()))).append('\'');
				if (value.getNano() > 0) {
					str.append('.');
					StringUtils.printInt(value.getNano(), 9, str);
				}
				return str;
			}

			@Override
			public Instant deserialize(ResultSet rs, int column) throws SQLException {
				Timestamp ts = rs.getTimestamp(column);
				if (ts == null)
					return null;
				else
					return ts.toInstant();
			}

			@Override
			public String getTypeName() {
				return "TIMESTAMP(9)";// TODO support other resolutions besides nanosecond
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(Instant v1, Instant v2) {
				// TODO support other resolutions besides nanosecond
				if (v1 == null || v2 == null)
					return false;
				if (v2.getNano() == v1.getNano() + 1)
					return v1.getEpochSecond() == v2.getEpochSecond();
				else if (v2.getNano() == 0)
					return v1.getNano() == 999_999_999 && v2.getEpochSecond() == v1.getEpochSecond() + 1;
				else
					return false;
			}
		};

		public static final JdbcColumn<Duration> DURATION_MILLIS = new JdbcColumn<Duration>() {
			@Override
			public StringBuilder serialize(Duration value, StringBuilder str) {
				return str.append(value == null ? "NULL" : value.toMillis());
			}

			@Override
			public Duration deserialize(ResultSet rs, int column) throws SQLException {
				Object value = rs.getObject(column);
				if (value == null)
					return null;
				else
					return Duration.ofMillis(((Number) value).longValue());
			}

			@Override
			public String getTypeName() {
				return "BIGINT";
			}

			@Override
			public boolean testsAdjacent() {
				return true;
			}

			@Override
			public boolean isAdjacent(Duration v1, Duration v2) {
				int v1Nano = v1.getNano() / 1_000_000;
				int v2Nano = v2.getNano() / 1_000_000;
				if (v2Nano == v1Nano + 1)
					return v1.getSeconds() == v2.getSeconds();
				else if (v2Nano == 0)
					return v1Nano == 999 && v2.getSeconds() == v1.getSeconds() + 1;
				else
					return false;
			}
		};

		public static final JdbcColumn<Duration> DURATION_SECONDS = new JdbcColumn<Duration>() {
			@Override
			public StringBuilder serialize(Duration value, StringBuilder str) {
				if (value == null) {
					str.append("NULL");
				}
				str.append(value.getSeconds());
				if (value.getNano() > 0) {
					str.append('.');
					StringUtils.printInt(value.getNano(), 9, str);
				}
				return str;
			}

			@Override
			public Duration deserialize(ResultSet rs, int column) throws SQLException {
				Object value = rs.getObject(column);
				if (value == null)
					return null;
				else {
					double num = ((Number) value).doubleValue();
					long seconds = (long) num;
					num -= seconds;
					if (num == 0.0 || num == -0.0)
						return Duration.ofSeconds(seconds);
					else {
						if (num < 0) {
							seconds--;
							num += 1;
						}
						return Duration.ofSeconds(seconds, (int) (num * 1E9));
					}
				}
			}

			@Override
			public String getTypeName() {
				return "INTEGER";
			}

			@Override
			public boolean testsAdjacent() {
				return false;
			}

			@Override
			public boolean isAdjacent(Duration v1, Duration v2) {
				return false;
			}
		};
	}

	public static final Builder DEFAULT;

	static {
		Builder builder = build();
		builder.supportColumnType(TypeTokens.get().INT, JdbcTypeSupport.INT);
		builder.supportColumnType(TypeTokens.get().LONG, JdbcTypeSupport.LONG);
		builder.supportColumnType(TypeTokens.get().DOUBLE, JdbcTypeSupport.DOUBLE);
		builder.supportColumnType(TypeTokens.get().STRING, JdbcTypeSupport.STRING);
		builder.supportColumnType(TypeTokens.get().BOOLEAN, JdbcTypeSupport.BOOLEAN);
		builder.supportColumnType(TypeTokens.get().of(Instant.class), JdbcTypeSupport.INSTANT);
		builder.supportColumnType(TypeTokens.get().of(Duration.class), JdbcTypeSupport.DURATION);
		builder.supportColumnType(TypeTokens.get().keyFor(Collection.class).wildCard(), JdbcTypeSupport.COLLECTION);

		DEFAULT = builder;
	}

	private static class ColumnSupportHolder<T> {
		final TypeToken<T> theType;
		final JdbcTypeSupport<T> support;

		ColumnSupportHolder(TypeToken<T> type, JdbcTypeSupport<T> support) {
			this.theType = TypeTokens.get().wrap(type);
			this.support = support;
		}

		<F extends T> JdbcColumn<F> getColumn(TypeToken<F> type, String columnTypeName, JdbcEntitySupport allSupport) {
			if (theType.isAssignableFrom(TypeTokens.get().wrap(type))) {
				return support.columnFor(type, columnTypeName != null ? columnTypeName : support.getDefaultColumnType(type),
					allSupport);
			} else
				return null;
		}
	}

	public static Builder build() {
		return new Builder();
	}

	public static class Builder {
		private final List<ColumnSupportHolder<?>> theColumnSupport = new ArrayList<>();
		private String theAutoIncrement;

		public Builder withAutoIncrement(String autoIncrement) {
			theAutoIncrement = autoIncrement;
			return this;
		}

		public <T> Builder supportColumnType(TypeToken<T> type, JdbcTypeSupport<T> columnSupport) {
			theColumnSupport.add(new ColumnSupportHolder<>(type, columnSupport));
			return this;
		}

		public Builder support(JdbcEntitySupport support) {
			theColumnSupport.addAll(support.getColumnSupport());
			return this;
		}

		public JdbcEntitySupport build() {
			return new JdbcEntitySupport(theAutoIncrement, theColumnSupport);
		}
	}

	private final String theAutoIncrement;
	private final List<ColumnSupportHolder<?>> theColumnSupport;

	public JdbcEntitySupport(String autoIncrement, List<ColumnSupportHolder<?>> columnSupport) {
		theAutoIncrement = autoIncrement;
		List<ColumnSupportHolder<?>> copy = new ArrayList<>(columnSupport.size());
		copy.addAll(columnSupport);
		theColumnSupport = Collections.unmodifiableList(copy);
	}

	public List<ColumnSupportHolder<?>> getColumnSupport() {
		return theColumnSupport;
	}

	public <F> JdbcColumn<F> getColumnSupport(TypeToken<F> type, String columnTypeName) {
		type = TypeTokens.get().wrap(type);
		for (ColumnSupportHolder<?> holder : theColumnSupport) {
			if (!holder.theType.isAssignableFrom(type))
				continue;
			JdbcColumn<F> column = ((ColumnSupportHolder<? super F>) holder).getColumn(type, columnTypeName, this);
			if (column != null)
				return column;
		}
		throw new IllegalArgumentException("Type " + type + " not supported");
	}

	public String getAutoIncrement() {
		return theAutoIncrement;
	}
}

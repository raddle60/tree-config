package com.raddle.config.tree.utils;

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class ReflectToStringBuilder {
	public static final int DEFAULT_MAX_LEVEL = 10;

	public static StringBuilder reflectToString(Object obj) {
		StringBuilder sb = new StringBuilder();
		reflectToString(obj, sb, 0);
		return sb;
	}

	@SuppressWarnings("unchecked")
	private static void reflectToString(Object obj, StringBuilder sb, int level) {
		if (level > DEFAULT_MAX_LEVEL) {
			sb.append("TO_MAX_LEVEL:" + DEFAULT_MAX_LEVEL);
			return;
		}
		if (obj == null) {
			sb.append("null");
			return;
		}
		if (obj.getClass().isArray()) {
			// 數組
			sb.append("[ ");
			for (int i = 0; i < Array.getLength(obj); i++) {
				if (i > 0) {
					sb.append(", ");
				}
				reflectToString(Array.get(obj, i), sb, level + 1);
			}
			sb.append(" ]");
		} else if (obj instanceof Collection) {
			// 集合
			sb.append("[ ");
			Collection c = (Collection) obj;
			int i = 0;
			for (Object object : c) {
				if (i > 0) {
					sb.append(", ");
				}
				reflectToString(object, sb, level + 1);
				i++;
			}
			sb.append(" ]");
		} else if (obj instanceof Map) {
			// MAP
			sb.append("{ ");
			Map map = (Map) obj;
			int i = 0;
			for (Object key : map.keySet()) {
				if (i > 0) {
					sb.append(", ");
				}
				reflectToString(key, sb, level + 1);
				sb.append(":");
				reflectToString(map.get(key), sb, level + 1);
				i++;
			}
			sb.append(" }");
		} else if (obj.getClass().getName().startsWith("java") || obj.getClass().getName().indexOf(".") == -1) {
			// jdk對象
			if (obj.getClass() == String.class) {
				sb.append("\"").append(obj).append("\"");
			} else if (obj instanceof Date) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS");
				sb.append(sdf.format((Date) obj));
			} else {
				sb.append(obj);
			}
			return;
		} else {
			// 自定義的對象
			try {
				sb.append("{ ");
				BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass());
				PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();
				int i = 0;
				for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
					// 不反射class
					if (propertyDescriptor.getName().equals("class")) {
						continue;
					}
					// 其他屬性
					if (propertyDescriptor.getReadMethod() != null) {
						if (i > 0) {
							sb.append(", ");
						}
						sb.append(propertyDescriptor.getName());
						sb.append(":");
						reflectToString(propertyDescriptor.getReadMethod().invoke(obj), sb, level + 1);
						i++;
					}
				}
				sb.append(" }");
			} catch (Exception e) {
				throw new RuntimeException(e.getMessage() + ", build message: " + sb, e);
			}
		}
	}
}

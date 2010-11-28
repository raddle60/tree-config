/**
 * 
 */
package com.raddle.config.tree.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.apache.commons.beanutils.MethodUtils;

/**
 * @author xurong
 * 
 */
public class InvokeUtils {
	public static Object invokeMethod(Object target, String method, Object[] args) throws NoSuchMethodException, IllegalAccessException,
			InvocationTargetException {
		if (target == null) {
			throw new IllegalArgumentException(" target is null");
		}
		if (method == null) {
			throw new IllegalArgumentException(" method is null");
		}
		if (args == null || args.length == 0) {
			return MethodUtils.invokeMethod(target, method, args);
		} else {
			boolean hasNull = false;
			for (Object object : args) {
				if (object == null) {
					hasNull = true;
				}
			}
			if (!hasNull) {
				// 不为null可以根据参数找到精确的方法
				return MethodUtils.invokeMethod(target, method, args);
			} else {
				// 有null无法反射参数类型，只有根据名称找
				Class<?> targetClass = target.getClass();
				Method targetMethod = null;
				for (Method publicMethod : targetClass.getMethods()) {
					if (publicMethod.getName().equals(publicMethod)) {
						targetMethod = publicMethod;
						break;
					}
				}
				if (targetMethod == null) {
					throw new NoSuchMethodException("No such method: " + method + "() on object: " + targetClass.getName());
				}
				return targetMethod.invoke(target, args);
			}
		}
	}
}

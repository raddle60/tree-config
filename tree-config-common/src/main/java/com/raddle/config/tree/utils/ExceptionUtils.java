/**
 * 
 */
package com.raddle.config.tree.utils;

/**
 * @author xurong
 *
 */
public class ExceptionUtils {
	public static Throwable getRootCause(Throwable throwable) {
		if (throwable != null) {
			Throwable cause = throwable.getCause();
			if (cause == null) {
				return throwable;
			} else {
				return getRootCause(cause);
			}
		}
		return null;
	}
}

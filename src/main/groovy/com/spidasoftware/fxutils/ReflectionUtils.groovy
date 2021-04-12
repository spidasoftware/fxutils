package com.spidasoftware.fxutils

import javax.management.ReflectionException
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/*
 ** Util class for our implementation of reflection
 */
class ReflectionUtils {

	/**
	 * @param Class clazz: Cannot be a sub class that inherits a method, this must be the class where the method exists in.
	 * @param Object object: instance of clazz.
	 * @param String methodName: name of the method that you want to run.
	 * @return Object: returns an object if the private method returns an object
	 * @throws InvocationTargetException
	 */
	static Object executePrivateMethod(Class clazz, Object object, String methodName, Object... args) throws InvocationTargetException, ReflectionException {
		Method method = clazz.getDeclaredMethod(methodName)
		method.setAccessible(true)
		return method.invoke(object, args)
	}

	/**
	 * @param Class clazz: Cannot be a sub class that inherits a method, this must be the class where the method exists in.
	 * @param Object object: instance of clazz.
	 * @param String fieldName: name of the field you want to retrieve.
	 * @return Object: the value for the field of {@code obj}
	 * @throws InvocationTargetException
	 */
	static Object getPrivateFieldValue(Class clazz, Object object, String fieldName) throws InvocationTargetException, ReflectionException {
		Field field = clazz.getDeclaredField(fieldName)
		field.setAccessible(true)
		Object value = field.get(object)
		return value
	}

	/**
	 * @param Class clazz: Cannot be a sub class that inherits a method, this must be the class where the method exists in.
	 * @param Object object: the object whose field should be modified.
	 * @param String fieldName: name of the field you want to retrieve.
	 * @param Object value : the new value for the field of {@code obj}
	 * @throws InvocationTargetException
	 */
	static void setPrivateFieldValue(Class clazz, Object object, String fieldName, Object value) throws InvocationTargetException, ReflectionException {
		Field field = clazz.getDeclaredField(fieldName)
		field.setAccessible(true)
		field.set(object, value)
	}
}

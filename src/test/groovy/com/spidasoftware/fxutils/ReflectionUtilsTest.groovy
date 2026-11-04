package com.spidasoftware.fxutils

import groovy.transform.CompileStatic
import spock.lang.Specification

import java.lang.reflect.InvocationTargetException

class ReflectionUtilsTest extends Specification {

	PrivateTestClass testClass = new PrivateTestClass()

	def "executePrivateMethod invokes a no-arg private method"() {
		expect:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "getString") == "Success"
	}

	def "executePrivateMethod passes arguments through to the method"() {
		expect:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "concat", "a", "b") == "ab"
	}

	def "executePrivateMethod returns null for void methods and applies side effects"() {
		when:
			Object result = ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "setPrivateString", "changed")
		then:
			result == null
			testClass.@privateString == "changed"
	}

	def "executePrivateMethod invokes private static methods"() {
		expect:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, null, "staticMethod") == "static"
	}

	def "executePrivateMethod throws NoSuchMethodException when the method does not exist"() {
		when:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "doesNotExist")
		then:
			thrown(NoSuchMethodException)
	}

	def "executePrivateMethod throws NoSuchMethodException when argument types do not match"() {
		when:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "concat", 1, 2)
		then:
			thrown(NoSuchMethodException)
	}

	def "executePrivateMethod does not find methods declared on a superclass"() {
		when:
			ReflectionUtils.executePrivateMethod(PrivateSubclass, new PrivateSubclass(), "getString")
		then:
			thrown(NoSuchMethodException)
	}

	def "executePrivateMethod wraps exceptions thrown by the target in InvocationTargetException"() {
		when:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "explode")
		then:
			InvocationTargetException ex = thrown()
			ex.cause instanceof IllegalStateException
			ex.cause.message == "boom"
	}

	def "executePrivateMethod throws IllegalArgumentException when object is not an instance of clazz"() {
		when:
			ReflectionUtils.executePrivateMethod(PrivateTestClass, "not the right type", "getString")
		then:
			thrown(IllegalArgumentException)
	}

	def "getPrivateFieldValue reads a private field"() {
		expect:
			ReflectionUtils.getPrivateFieldValue(PrivateTestClass, testClass, "privateField") == "privateField"
	}

	def "getPrivateFieldValue reads a private static field"() {
		expect:
			ReflectionUtils.getPrivateFieldValue(PrivateTestClass, null, "STATIC_FIELD") == "static field"
	}

	def "getPrivateFieldValue throws NoSuchFieldException when the field does not exist"() {
		when:
			ReflectionUtils.getPrivateFieldValue(PrivateTestClass, testClass, "missing")
		then:
			thrown(NoSuchFieldException)
	}

	def "getPrivateFieldValue does not find fields declared on a superclass"() {
		when:
			ReflectionUtils.getPrivateFieldValue(PrivateSubclass, new PrivateSubclass(), "privateField")
		then:
			thrown(NoSuchFieldException)
	}

	def "setPrivateFieldValue writes a private field"() {
		when:
			ReflectionUtils.setPrivateFieldValue(PrivateTestClass, testClass, "privateString", "newValue")
		then:
			ReflectionUtils.getPrivateFieldValue(PrivateTestClass, testClass, "privateString") == "newValue"
	}

	def "setPrivateFieldValue can set a field to null"() {
		when:
			ReflectionUtils.setPrivateFieldValue(PrivateTestClass, testClass, "privateString", null)
		then:
			ReflectionUtils.getPrivateFieldValue(PrivateTestClass, testClass, "privateString") == null
	}

	def "setPrivateFieldValue throws NoSuchFieldException when the field does not exist"() {
		when:
			ReflectionUtils.setPrivateFieldValue(PrivateTestClass, testClass, "missing", "value")
		then:
			thrown(NoSuchFieldException)
	}

	def "setPrivateFieldValue throws IllegalArgumentException when the value type is incompatible"() {
		when:
			ReflectionUtils.setPrivateFieldValue(PrivateTestClass, testClass, "privateString", 42)
		then:
			thrown(IllegalArgumentException)
	}
}

@CompileStatic
class PrivateTestClass {
	private static final String STATIC_FIELD = "static field"
	private String privateField = "privateField"
	private String privateString = ""

	private String getString() {
		return "Success"
	}

	private String concat(String a, String b) {
		return a + b
	}

	private void setPrivateString(String privateString) {
		this.privateString = privateString
	}

	private static String staticMethod() {
		return "static"
	}

	private void explode() {
		throw new IllegalStateException("boom")
	}
}

@CompileStatic
class PrivateSubclass extends PrivateTestClass {
}

package com.spidasoftware.fxutils

import groovy.transform.CompileStatic
import spock.lang.Specification

@CompileStatic
class ReflectionUtilsTest extends Specification {

	def "ExecutePrivateMethod"() {
		setup:
			PrivateTestClass testClass = new PrivateTestClass()
		when:
			def string = ReflectionUtils.executePrivateMethod(PrivateTestClass, testClass, "getString")
		then:
			string == "Success"
	}

	def "test getPrivateField"() {
		setup:
			PrivateTestClass testClass = new PrivateTestClass()
		when:
			def string = ReflectionUtils.getPrivateFieldValue(PrivateTestClass, testClass, "privateField")
		then:
			string == "privateField"
	}

	def "test setPrivateFieldValue"() {
		setup:
			PrivateTestClass testClass = new PrivateTestClass()
		when:
			ReflectionUtils.setPrivateFieldValue(PrivateTestClass, testClass, "privateString", "newValue")
			String value = ReflectionUtils.getPrivateFieldValue(PrivateTestClass, testClass, "privateString")
		then:
			value == "newValue"
	}
}

@CompileStatic
class PrivateTestClass {
	private String privateField = "privateField"

	private String getString() {
		return "Success"
	}

	private String privateString = ""

	private void setPrivateString(String privateString) {
		this.privateString = privateString
	}
}


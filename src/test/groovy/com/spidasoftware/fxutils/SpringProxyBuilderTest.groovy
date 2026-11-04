package com.spidasoftware.fxutils

import javafx.beans.NamedArg
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class SpringProxyBuilderTest extends Specification {

	ApplicationContext context = Mock()

	def "findConstructors maps every fully-annotated constructor and skips those with unnamed parameters"() {
		when:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
		then:
			builder.@constructors.size() == 3
			builder.@constructors.find { it.isEmpty() } != null
			builder.@constructors.find { it.keySet() == ["name"] as Set } != null
			builder.@constructors.find { it.keySet() == ["name", "size"] as Set } != null
	}

	def "findConstructors records name, default value and type of each named argument"() {
		when:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			Map<String, SpringProxyBuilder.Argument> constructor = builder.@constructors.find { it.size() == 2 }
		then:
			constructor.name.name == "name"
			constructor.name.defaultValue == ""
			constructor.name.type == String
			constructor.size.name == "size"
			constructor.size.defaultValue == "7"
			constructor.size.type == int
	}

	def "a class with only unannotated constructors yields no usable constructors and build returns null"() {
		given:
			SpringProxyBuilder<Unbuildable> builder = new SpringProxyBuilder<>(Unbuildable, context)
		when:
			Unbuildable result = builder.build()
		then:
			builder.@constructors.isEmpty()
			result == null
			0 * context._
	}

	def "pickConstructor chooses the constructor matching the most provided properties"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			Set<String> properties = ["name", "size", "label"] as Set
		when:
			Map<String, SpringProxyBuilder.Argument> picked = builder.pickConstructor(properties)
		then:
			picked.keySet() == ["name", "size"] as Set
	}

	def "pickConstructor removes properties consumed by the constructor from the provided set"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			Set<String> properties = ["name", "size", "label"] as Set
		when:
			builder.pickConstructor(properties)
		then:
			properties == ["label"] as Set
	}

	def "pickConstructor prefers the constructor with fewest parameters on a tie"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
		when:
			Map picked = builder.pickConstructor(properties as Set)
		then:
			picked.keySet() == expected as Set
		where:
			properties         | expected
			[]                 | []
			["label"]          | []
			["name"]           | ["name"]
			["name", "label"]  | ["name"]
	}

	def "getValue returns the provided value coerced to the argument type"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			builder.put("size", "42")
			SpringProxyBuilder.Argument argument = new SpringProxyBuilder.Argument(name: "size", defaultValue: "7", type: int)
		expect:
			builder.getValue(argument) == 42
	}

	def "getValue falls back to the NamedArg default when no value was provided"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			SpringProxyBuilder.Argument argument = new SpringProxyBuilder.Argument(name: "size", defaultValue: "7", type: int)
		expect:
			builder.getValue(argument) == 7
	}

	def "build requests the bean from the context using the uncapitalized simple class name"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
		when:
			Widget result = builder.build()
		then:
			1 * context.getBean("widget", [] as Object[]) >> new Widget()
			result != null
	}

	def "build passes coerced constructor arguments to the context in declaration order"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			builder.put("name", "gadget")
			builder.put("size", "3")
		when:
			builder.build()
		then:
			1 * context.getBean("widget", ["gadget", 3] as Object[]) >> new Widget("gadget", 3)
	}

	def "build uses defaults for constructor arguments that were not provided"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			builder.put("size", "3")
		when:
			builder.build()
		then:
			1 * context.getBean("widget", ["", 3] as Object[]) >> new Widget("", 3)
	}

	def "build sets remaining properties on the created instance with type coercion"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			builder.put("name", "gadget")
			builder.put("label", "A label")
			builder.put("enabled", "true")
			context.getBean("widget", ["gadget"] as Object[]) >> new Widget("gadget")
		when:
			Widget result = builder.build()
		then:
			result.name == "gadget"
			result.label == "A label"
			result.enabled
	}

	def "build does not re-set properties that were consumed by the constructor"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			builder.put("name", "gadget")
			Widget widget = Spy(new Widget("gadget"))
			context.getBean("widget", ["gadget"] as Object[]) >> widget
		when:
			builder.build()
		then:
			0 * widget.setName(_)
	}

	def "build returns null when the context throws"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			context.getBean(_, _) >> { throw new RuntimeException("no such bean") }
		expect:
			builder.build() == null
	}

	def "build returns null when a provided property does not exist on the bean"() {
		given:
			SpringProxyBuilder<Widget> builder = new SpringProxyBuilder<>(Widget, context)
			builder.put("nonexistent", "value")
			context.getBean(_, _) >> new Widget()
		expect:
			builder.build() == null
	}

	static class Widget {
		String name
		int size
		boolean enabled
		String label

		Widget() {}

		Widget(@NamedArg("name") String name) {
			this.name = name
		}

		Widget(@NamedArg("name") String name, @NamedArg(value = "size", defaultValue = "7") int size) {
			this.name = name
			this.size = size
		}

		Widget(@NamedArg("name") String name, int size, long unnamed) {
			this.name = name
			this.size = size
		}
	}

	static class Unbuildable {
		Unbuildable(String unnamed) {}
	}
}

package com.spidasoftware.fxutils

import com.sun.javafx.fxml.BeanAdapter  // jfx11 this requires opening javafx.fxml/com.sun.javafx.fxml
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import javafx.beans.NamedArg
import javafx.util.Builder
import org.apache.commons.beanutils.PropertyUtils
import org.apache.commons.lang3.text.WordUtils
import org.springframework.context.ApplicationContext

import java.lang.annotation.Annotation
import java.lang.reflect.Constructor

/**
 * We need a Builder that does the same logic as the normal Proxy builder to pick a constructor, apply set values, etc,
 * but that calls through to the ApplicationContext instead of calling the constructor.
 *
 * We extend map because of how FXML handles builders. If they implement map it doesn't look for individual setters for
 * properties. The properties are set on the Builder, and we get them and put them into the object.
 *
 * Because of groovy weirdness, this means, we have to use this.@property or risk accidentally putting extra things in
 * the map
 */
@Slf4j
@CompileStatic
class SpringProxyBuilder<T> extends HashMap<String, Object> implements Builder<T> {

	// the application context used to construct the object
	ApplicationContext context

	// The type of object to construct
	Class<T> type

	/**
	 * An argument to a constructor
	 */
	static class Argument {
		// name in NamedArg
		String name
		// default value in NamedArg
		String defaultValue
		// Type of object for argument
		Class<?> type
	}

	// A list of all constructors. Each constructor is represented as a map of its arguments
	List<Map<String,Argument>> constructors = []

	/** only for testing **/
	protected SpringProxyBuilder () {

	}

	// We might be able to do this with ApplicationContextAware, but it seemed to cause me more trouble?
	SpringProxyBuilder(Class<T> type, ApplicationContext context) {
		this.@context = context
		this.@type = type
		try {
			findConstructors()
		} catch (Exception ex) {
			// if something went wrong finding constructors, return an error
			constructors.clear()
			log.error("Could not instantiate builder for class ${this.@type.getSimpleName()} because an error occured finding constructors.", ex)
		}
	}

	/**
	 * Builds a T
	 * @return T with all properties set that have defaults or have been set on this.
	 */
	@Override
	T build() {
		if (constructors.isEmpty()) {
			log.error("Could not create object of type ${this.@type.getSimpleName()} because no constructors were found.")
			return null
		}
		try {
			// get a copy of all provided arguments by name
			Set<String> providedProperties = new HashSet<>(keySet())

			// pick the best constructor given those arguments, and remove properties that will be set by the constructor
			Map<String, Argument> constructor = pickConstructor(providedProperties)

			// create the argument array to pass to the constructor
			List constructorArgs = []
			constructor.values().each { Argument argument ->
				Object value = getValue(argument)
				constructorArgs.add (value)
			}
			T object = createInstance(constructorArgs.toArray())

			// set the properties we have left after construction.
			providedProperties.each {String property ->
				Class<? extends Void> propertyType = getTypeForProperty(object, property)
				Object propertyValue = get(property)
				object[property] = BeanAdapter.coerce(propertyValue, propertyType)
			}
			return object
		} catch (Exception ex) {
			this.@log.error(ex.toString(), ex)
			return null
		}
	}

	/**
	 * Generates one map of named arguments for every constructors, and puts those in a list
	 * Constructors without named arguments are skipped
	 * @throws Exception
	 */
	protected void findConstructors() throws Exception {
		type.constructors.each {Constructor constructor ->
			def arguments = [:]
			Class<?>[] types = constructor.getParameterTypes();
			Annotation[][] annotations = constructor.getParameterAnnotations();
			
			if (types.size() > 0) {
				// have a constructor with arguments. See if those arguments are annotated property
				this.@log.trace("Constructor with ${types.size()} parameters found")
				boolean unnamedArgument = false
				types.eachWithIndex { Class<?> parameterType, int index ->
					Annotation[] parameterAnnotations = annotations[index]
					NamedArg namedArg = parameterAnnotations.find { it instanceof NamedArg } as NamedArg
					// if we find a named argument, add it to our list of arguments
					if (namedArg) {
						Argument argument = new Argument(name: namedArg.value(), defaultValue: namedArg.defaultValue(), type: parameterType)
						this.@log.trace("Found named argument ${argument.name}")
						arguments.put(argument.name, argument)
					} else {
						// otherwise, go to the next constructor. We don't handle constructors without annotations.
						this.@log.trace("Parameter with no named argument: ${parameterType.simpleName}")
						unnamedArgument = true
						return // return from closure AKA continue
					}
				}
				// if everything is annotated, add the constructor to our list.
				if (!unnamedArgument) {
					this.@constructors.add(arguments)
					this.@log.trace("Constructor ${arguments.toMapString()} found")
				}
			} else {
				// default constructor
				this.@log.trace("Default constructor found")
				this.@constructors.add([:])
			}
		}
	}

	/**
	 * Picks the best matching constructor, and removes any properties from properties that are not used in it
	 * @param properties
	 * @return
	 */
	protected Map<String, Argument> pickConstructor(Set<String> properties) {
		// find the constructor that matches the most of the properties we have.
		int maxMatching = 0
		Map bestConstructor = null
		constructors.each { Map constructor ->
			int matchingPropertyCount = properties.count { String key ->
				constructor.containsKey(key)
			} as int
			if (bestConstructor == null) {
				bestConstructor = constructor
				maxMatching = matchingPropertyCount
			}
			if (matchingPropertyCount > maxMatching) {
				bestConstructor = constructor
				maxMatching = matchingPropertyCount
			} else if (matchingPropertyCount == maxMatching && bestConstructor != null) {
				bestConstructor = [constructor, bestConstructor].min { Map map -> return map.keySet().size() }
			}
		}

		// remove all properties that will be set in constructor from properties to set.
		properties.removeAll(bestConstructor.keySet())
		return bestConstructor
	}

	/**
	 * Get the value for the argument if it exists in our map, or return the default value
	 */
	protected Object getValue(Argument argument) {
		Object value = get(argument.name)
		if (value == null) {
			value = argument.defaultValue
		}
		return BeanAdapter.coerce(value, argument.type)
	}

	/**
	 * Get the type to coerce a property to
	 */
	protected Class<?> getTypeForProperty(T bean, String property) throws Exception {
		return PropertyUtils.getPropertyType(bean, property)

	}

	/**
	 * Get an instance from the spring context.
	 */
	protected T createInstance(Object[] args) throws Exception {
		String name = WordUtils.uncapitalize(this.@type.simpleName)
		return this.@context.getBean(name, args) as T
	}
}

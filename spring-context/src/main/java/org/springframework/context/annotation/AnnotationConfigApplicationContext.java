/*
 * Copyright 2002-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.context.annotation;

import java.util.Arrays;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.Assert;

/**
 * 这是一个标准的Spring容器，接受的组件为 @Configuration、@Component或者符合JSR-330修饰的类。
 * 他可以一个类一个类的进行注册，也可以通过扫描包路径进行注册。
 * 当项目中存在多个 @Configuration 类时，后扫描到的 @Configuration 类中定义的同名 @Bean 方法，
 * 会覆盖（override）先扫描到的 @Configuration 类中同名 @Bean 的定义。
 * 这是一种故意设计的行为，Spring 允许开发者利用这个规则来实现“配置覆盖”或“配置增强/替换”。
 */

/**
 * Standalone application context, accepting <em>component classes</em> as input &mdash;
 * in particular {@link Configuration @Configuration}-annotated classes, but also plain
 * {@link org.springframework.stereotype.Component @Component} types and JSR-330 compliant
 * classes using {@code jakarta.inject} annotations.
 *
 * <p>Allows for registering classes one by one using {@link #register(Class...)}
 * as well as for classpath scanning using {@link #scan(String...)}.
 *
 * <p>In case of multiple {@code @Configuration} classes, {@link Bean @Bean} methods
 * defined in later classes will override those defined in earlier classes. This can
 * be leveraged to deliberately override certain bean definitions via an extra
 * {@code @Configuration} class.
 *
 * <p>See {@link Configuration @Configuration}'s javadoc for usage examples.
 *
 * @author Juergen Hoeller
 * @author Chris Beams
 * @since 3.0
 * @see #register
 * @see #scan
 * @see AnnotatedBeanDefinitionReader
 * @see ClassPathBeanDefinitionScanner
 * @see org.springframework.context.support.GenericXmlApplicationContext
 */
public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {

	// 这个类组件用于读取和注册 @Configuration、@Component 等注解的类，他的参数是类，可传入多个或者数组，比如 MyClass.clss
	private final AnnotatedBeanDefinitionReader reader;
	// 这个类组件则从也是查找组件类，但是它是从包中扫描,传入的参数是包名，比如 "org.springframework.context.annotation"
	private final ClassPathBeanDefinitionScanner scanner;


	// 创建AnnotationConfigApplicationContext对象，
	// 并且使用默认的DefaultListableBeanFactory作为底层的BeanFactory。
	// 同时创建两个类组件，一个是 AnnotatedBeanDefinitionReader，一个是 ClassPathBeanDefinitionScanner
	/**
	 * Create a new AnnotationConfigApplicationContext that needs to be populated
	 * through {@link #register} calls and then manually {@linkplain #refresh refreshed}.
	 */
	public AnnotationConfigApplicationContext() {
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	// 创建AnnotationConfigApplicationContext对象，
	// 创建两个类组件，一个是 AnnotatedBeanDefinitionReader，一个是 ClassPathBeanDefinitionScanner
	// 并传入一个DefaultListableBeanFactory的子类对象作为底层的BeanFactory。
	/**
	 * Create a new AnnotationConfigApplicationContext with the given DefaultListableBeanFactory.
	 * @param beanFactory the DefaultListableBeanFactory instance to use for this context
	 */
	public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
		super(beanFactory);
		this.reader = new AnnotatedBeanDefinitionReader(this);
		this.scanner = new ClassPathBeanDefinitionScanner(this);
	}

	/**
	 * 创建AnnotationConfigApplicationContext对象，
	 * 默认使用DefaultListableBeanFactory作为底层的BeanFactory。
	 * 创建两个类组件，一个是 AnnotatedBeanDefinitionReader，一个是 ClassPathBeanDefinitionScanner
	 * 同时传入一个组件类，比如 MyClass.class
	 * 且该构造方法自动注册class和刷新上下文，即创建bean对象。
	 */
	/**
	 * Create a new AnnotationConfigApplicationContext, deriving bean definitions
	 * from the given component classes and automatically refreshing the context.
	 * @param componentClasses one or more component classes &mdash; for example,
	 * {@link Configuration @Configuration} classes
	 */
	public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
		this();
		// 这个方法将BeanDefinition注册到BeanDefinitionRegistry中，
		register(componentClasses);
		refresh(); // 刷新上下文，这个方法非常重要，bean创建的生命周期在这里完成
	}

	// 这个方法与上面的方法一样，不同的是传入包路径而已
	/**
	 * Create a new AnnotationConfigApplicationContext, scanning for components
	 * in the given packages, registering bean definitions for those components,
	 * and automatically refreshing the context.
	 * @param basePackages the packages to scan for component classes
	 */
	public AnnotationConfigApplicationContext(String... basePackages) {
		this();
		scan(basePackages);
		refresh();
	}


	// 将自定义Environment对象设置给AnnotatedBeanDefinitionReader和ClassPathBeanDefinitionScanner
	/**
	 * Propagate the given custom {@code Environment} to the underlying
	 * {@link AnnotatedBeanDefinitionReader} and {@link ClassPathBeanDefinitionScanner}.
	 */
	@Override
	public void setEnvironment(ConfigurableEnvironment environment) {
		super.setEnvironment(environment);
		this.reader.setEnvironment(environment);
		this.scanner.setEnvironment(environment);
	}

	// 将自定义BeanNameGenerator设置给AnnotatedBeanDefinitionReader和ClassPathBeanDefinitionScanner
	// 该接口的作用是定义bean的名称，比如 @Component("myBean")，那么myBean就是bean的名称
	// 该方法必须在调用register或者scan方法之前调用
	/**
	 * Provide a custom {@link BeanNameGenerator} for use with {@link AnnotatedBeanDefinitionReader}
	 * and/or {@link ClassPathBeanDefinitionScanner}.
	 * <p>Default is {@code AnnotationBeanNameGenerator}.
	 * <p>When processing {@link Configuration @Configuration} classes, a
	 * {@link ConfigurationBeanNameGenerator} (such as
	 * {@link FullyQualifiedConfigurationBeanNameGenerator}) also determines the
	 * default names for {@link Bean @Bean} methods without an explicit {@code name}
	 * attribute.
	 * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
	 * and/or {@link #scan(String...)}.
	 * @see AnnotatedBeanDefinitionReader#setBeanNameGenerator
	 * @see ClassPathBeanDefinitionScanner#setBeanNameGenerator
	 * @see AnnotationBeanNameGenerator
	 * @see FullyQualifiedAnnotationBeanNameGenerator
	 * @see FullyQualifiedConfigurationBeanNameGenerator
	 */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.reader.setBeanNameGenerator(beanNameGenerator);
		this.scanner.setBeanNameGenerator(beanNameGenerator);
		getBeanFactory().registerSingleton(
				AnnotationConfigUtils.CONFIGURATION_BEAN_NAME_GENERATOR, beanNameGenerator);
	}

	// 将自定义ScopeMetadataResolver设置给AnnotatedBeanDefinitionReader和ClassPathBeanDefinitionScanner
	// 该接口的作用是定义bean的scope，比如 singleton、prototype等
	// 该方法必须在调用register或者scan方法之前调用
	/**
	 * Set the {@link ScopeMetadataResolver} to use for registered component classes.
	 * <p>The default is an {@link AnnotationScopeMetadataResolver}.
	 * <p>Any call to this method must occur prior to calls to {@link #register(Class...)}
	 * and/or {@link #scan(String...)}.
	 */
	public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
		this.reader.setScopeMetadataResolver(scopeMetadataResolver);
		this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
	}


	//---------------------------------------------------------------------
	// Implementation of AnnotationConfigRegistry
	//---------------------------------------------------------------------

	// 注册一个或多个组件类以供处理。注册的含义是将bean的一些信息，如bean的名称，scope等信息登记到BeanDefinitionRegistry中，以供实例化及
	// 后续使用，注册完成后，必须调用refresh方法，才能完成对bean的创建，刷新上下文。
	/**
	 * Register one or more component classes to be processed.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 * @param componentClasses one or more component classes &mdash; for example,
	 * {@link Configuration @Configuration} classes
	 * @see #scan(String...)
	 * @see #refresh()
	 */
	@Override
	public void register(Class<?>... componentClasses) {
		Assert.notEmpty(componentClasses, "At least one component class must be specified");
		StartupStep registerComponentClass = getApplicationStartup().start("spring.context.component-classes.register")
				.tag("classes", () -> Arrays.toString(componentClasses));
		this.reader.register(componentClasses);
		registerComponentClass.end();
	}

	// 与register方法作用一致，但是传入的参数是包路径。
	/**
	 * Perform a scan within the specified base packages.
	 * <p>Note that {@link #refresh()} must be called in order for the context
	 * to fully process the new classes.
	 * @param basePackages the packages to scan for component classes
	 * @see #register(Class...)
	 * @see #refresh()
	 */
	@Override
	public void scan(String... basePackages) {
		Assert.notEmpty(basePackages, "At least one base package must be specified");
		StartupStep scanPackages = getApplicationStartup().start("spring.context.base-packages.scan")
				.tag("packages", () -> Arrays.toString(basePackages));
		this.scanner.scan(basePackages);
		scanPackages.end();
	}


	//---------------------------------------------------------------------
	// Adapt superclass registerBean calls to AnnotatedBeanDefinitionReader
	//---------------------------------------------------------------------

	// 重写父类（GenericApplicationContext ）的registerBean方法
	// 转到AnnotatedBeanDefinitionReader的registerBean方法
	@Override
	public <T> void registerBean(@Nullable String beanName, Class<T> beanClass,
			@Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {

		this.reader.registerBean(beanClass, beanName, supplier, customizers);
	}

}

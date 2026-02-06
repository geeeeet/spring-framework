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

package org.springframework.beans.factory.config;

import java.util.Iterator;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Configuration interface to be implemented by most listable bean factories.
 * In addition to {@link ConfigurableBeanFactory}, it provides facilities to
 * analyze and modify bean definitions, and to pre-instantiate singletons.
 *
 * <p>This subinterface of {@link org.springframework.beans.factory.BeanFactory}
 * is not meant to be used in normal application code: Stick to
 * {@link org.springframework.beans.factory.BeanFactory} or
 * {@link org.springframework.beans.factory.ListableBeanFactory} for typical
 * use cases. This interface is just meant to allow for framework-internal
 * plug'n'play even when needing access to bean factory configuration methods.
 *
 * @author Juergen Hoeller
 * @since 03.11.2003
 * @see org.springframework.context.support.AbstractApplicationContext#getBeanFactory()
 */
public interface ConfigurableListableBeanFactory
		extends ListableBeanFactory, AutowireCapableBeanFactory, ConfigurableBeanFactory {

	/**
	 * Ignore the given dependency type for autowiring:
	 * for example, String. Default is none.
	 * @param type the dependency type to ignore
	 */
	void ignoreDependencyType(Class<?> type);

	/**
	 * 方法名: ignoreDependencyInterface
	 * 作用: 用于忽略指定的依赖接口，使其在自动装配（autowiring）过程中不被考虑。
	 * 参数:
	 * Class<?> ifc: 需要忽略的依赖接口类型。
	 * 该方法提供了一种机制，用于控制哪些接口不应参与 Spring 的自动装配过程，从而避免不必要的依赖注入行为。
	 * 默认是只有BeanFactoryAware被忽略，如果需要忽略其他依赖接口，可以多次调用该方法。
	 */
	/**
	 * Ignore the given dependency interface for autowiring.
	 * <p>This will typically be used by application contexts to register
	 * dependencies that are resolved in other ways, like BeanFactory through
	 * BeanFactoryAware or ApplicationContext through ApplicationContextAware.
	 * <p>By default, only the BeanFactoryAware interface is ignored.
	 * For further types to ignore, invoke this method for each type.
	 * @param ifc the dependency interface to ignore
	 * @see org.springframework.beans.factory.BeanFactoryAware
	 * @see org.springframework.context.ApplicationContextAware
	 */
	void ignoreDependencyInterface(Class<?> ifc);

	/**
	 *这个方法是 Spring IoC 容器（BeanFactory / ApplicationContext）里一个非常重要的“后门”功能。它的核心作用一句话概括：
	 *
	 *  让某些根本没有注册成 Bean 的特殊对象（比如容器自己、ApplicationContext、BeanFactory 等），也能被 @Autowired
	 * 、构造函数注入等方式自动注入给其他 Bean。
	 *
	 * 为什么需要这个方法？（最常见的痛点）正常情况下，@Autowired
	 * 只能注入已经注册成 Bean 的东西。但在实际开发中，有些对象非常特殊：
	 *    1、它们本身就是容器的一部分（比如当前的 ApplicationContext）
	 *    2、它们不适合也不应该注册成普通的 Bean（因为它们是容器级单例，生命周期和容器绑定）
	 *    3、但很多业务 Bean 又确实需要拿到它们（比如想动态 getBean、发布事件、读取环境变量等）
	 *
	 * autowiredValue 可以是 ObjectFactory（可以延迟加载）
	 * 比如：
	 * beanFactory.registerResolvableDependency(DataSource.class,
	 *     (ObjectFactory<DataSource>) () -> createDataSourceOnDemand());
	 * 只有真正用到时才会调用 createDataSourceOnDemand() 创建数据源。
	 *
	 * 如果没有这个机制，你就只能用 Aware 接口（ApplicationContextAware、BeanFactoryAware）来“被动接收”，而不能用更现代的 @Autowired
	 *  方式注入。registerResolvableDependency 就是为了解决这个矛盾，让这些“容器原生对象”也能像普通 Bean 一样被 @Autowired。
	 *
	 *  该方法与Aware的区别在于，Aware是被动接收，而registerResolvableDependency是主动注入。
	 *  即Aware 是“推”，这个是“拉”
	 */
	/**
	 * Register a special dependency type with corresponding autowired value.
	 * <p>This is intended for factory/context references that are supposed
	 * to be autowirable but are not defined as beans in the factory:
	 * for example, a dependency of type ApplicationContext resolved to the
	 * ApplicationContext instance that the bean is living in.
	 * <p>Note: There are no such default types registered in a plain BeanFactory,
	 * not even for the BeanFactory interface itself.
	 * @param dependencyType the dependency type to register. This will typically
	 * be a base interface such as BeanFactory, with extensions of it resolved
	 * as well if declared as an autowiring dependency (for example, ListableBeanFactory),
	 * as long as the given value actually implements the extended interface.
	 * @param autowiredValue the corresponding autowired value. This may also be an
	 * implementation of the {@link org.springframework.beans.factory.ObjectFactory}
	 * interface, which allows for lazy resolution of the actual target value.
	 */
	void registerResolvableDependency(Class<?> dependencyType, @Nullable Object autowiredValue);

	/**
	 * Determine whether the specified bean qualifies as an autowire candidate,
	 * to be injected into other beans which declare a dependency of matching type.
	 * <p>This method checks ancestor factories as well.
	 * @param beanName the name of the bean to check
	 * @param descriptor the descriptor of the dependency to resolve
	 * @return whether the bean should be considered as autowire candidate
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 */
	boolean isAutowireCandidate(String beanName, DependencyDescriptor descriptor)
			throws NoSuchBeanDefinitionException;

	/**
	 * Return the registered BeanDefinition for the specified bean, allowing access
	 * to its property values and constructor argument value (which can be
	 * modified during bean factory post-processing).
	 * <p>A returned BeanDefinition object should not be a copy but the original
	 * definition object as registered in the factory. This means that it should
	 * be castable to a more specific implementation type, if necessary.
	 * <p><b>NOTE:</b> This method does <i>not</i> consider ancestor factories.
	 * It is only meant for accessing local bean definitions of this factory.
	 * @param beanName the name of the bean
	 * @return the registered BeanDefinition
	 * @throws NoSuchBeanDefinitionException if there is no bean with the given name
	 * defined in this factory
	 */
	BeanDefinition getBeanDefinition(String beanName) throws NoSuchBeanDefinitionException;

	/**
	 * Return a unified view over all bean names managed by this factory.
	 * <p>Includes bean definition names as well as names of manually registered
	 * singleton instances, with bean definition names consistently coming first,
	 * analogous to how type/annotation specific retrieval of bean names works.
	 * @return the composite iterator for the bean names view
	 * @since 4.1.2
	 * @see #containsBeanDefinition
	 * @see #registerSingleton
	 * @see #getBeanNamesForType
	 * @see #getBeanNamesForAnnotation
	 */
	Iterator<String> getBeanNamesIterator();

	/**
	 * Clear the merged bean definition cache, removing entries for beans
	 * which are not considered eligible for full metadata caching yet.
	 * <p>Typically triggered after changes to the original bean definitions,
	 * for example, after applying a {@link BeanFactoryPostProcessor}. Note that metadata
	 * for beans which have already been created at this point will be kept around.
	 * @since 4.2
	 * @see #getBeanDefinition
	 * @see #getMergedBeanDefinition
	 */
	void clearMetadataCache();

	/**
	 * Freeze all bean definitions, signalling that the registered bean definitions
	 * will not be modified or post-processed any further.
	 * <p>This allows the factory to aggressively cache bean definition metadata
	 * going forward, after clearing the initial temporary metadata cache.
	 * @see #clearMetadataCache()
	 * @see #isConfigurationFrozen()
	 */
	void freezeConfiguration();

	/**
	 * Return whether this factory's bean definitions are frozen,
	 * i.e. are not supposed to be modified or post-processed any further.
	 * @return {@code true} if the factory's configuration is considered frozen
	 * @see #freezeConfiguration()
	 */
	boolean isConfigurationFrozen();

	/**
	 * Mark current thread as main bootstrap thread for singleton instantiation,
	 * with lenient bootstrap locking applying for background threads.
	 * <p>Any such marker is to be removed at the end of the managed bootstrap in
	 * {@link #preInstantiateSingletons()}.
	 * @since 6.2.12
	 * @see #setBootstrapExecutor
	 * @see #preInstantiateSingletons()
	 */
	default void prepareSingletonBootstrap() {
	}

	/**
	 * Ensure that all non-lazy-init singletons are instantiated, also considering
	 * {@link org.springframework.beans.factory.FactoryBean FactoryBeans}.
	 * Typically invoked at the end of factory setup, if desired.
	 * @throws BeansException if one of the singleton beans could not be created.
	 * Note: This may have left the factory with some beans already initialized!
	 * Call {@link #destroySingletons()} for full cleanup in this case.
	 * @see #prepareSingletonBootstrap()
	 * @see #destroySingletons()
	 */
	void preInstantiateSingletons() throws BeansException;

}

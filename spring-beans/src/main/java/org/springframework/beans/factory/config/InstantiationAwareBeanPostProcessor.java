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

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;

/**
 * InstantiationAWareBeanPostProcessor这个接口继承了BeanPostProcessor接口，
 * 它虽然继承BeanPostProcessor接口，但是它并不是一个普通的BeanPostProcessor，而是用来
 * 实例化bean的，而不是bean初始化前后处理。
 * 新增两个方法：postProcessBeforeInstantiation和postProcessAfterInstantiation。
 * 它的作用是限制生成默认的bean实例，转而生成代理对象，自定义实例。比如连接池对象，懒加载对象等。
 *
 */

/**
 * Subinterface of {@link BeanPostProcessor} that adds a before-instantiation callback,
 * and a callback after instantiation but before explicit properties are set or
 * autowiring occurs.
 *
 * <p>Typically used to suppress default instantiation for specific target beans,
 * for example to create proxies with special TargetSources (pooling targets,
 * lazily initializing targets, etc), or to implement additional injection strategies
 * such as field injection.
 *
 * <p><b>NOTE:</b> This interface is a special purpose interface, mainly for
 * internal use within the framework. It is recommended to implement the plain
 * {@link BeanPostProcessor} interface as far as possible.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @since 1.2
 * @see org.springframework.aop.framework.autoproxy.AbstractAutoProxyCreator#setCustomTargetSourceCreators
 * @see org.springframework.aop.framework.autoproxy.target.LazyInitTargetSourceCreator
 */
public interface InstantiationAwareBeanPostProcessor extends BeanPostProcessor {

	/**
	 * 该方法在创建bean之前被调用，也就是它可以决定要不要生成bean对象或者生成什么样（代理对象）的bean对象。
	 * 它可以生成一个代理对象代替默认生成的对象。
	 * 如果返回null，则使用默认的生成bean对象的方式生成bean对象；
	 * 如果返回不是null，则使用该方法生成的代理对象。但是创建bean的进程会被中断，后续只能执行BeanPostProcessor回调
	 * 的postProcessAfterInitialization方法进行处理。
	 * 典型的懒加载，连接池等应用，或者是一些属性额外的注入操作。
	 *
	 * 这个方法很强大，也很危险，处理不好可能无法生成bean对象，
	 * 默认返回null，即使用常规方式生成bean对象。
	 *
	 */
	/**
	 * Apply this BeanPostProcessor <i>before the target bean gets instantiated</i>.
	 * The returned bean object may be a proxy to use instead of the target bean,
	 * effectively suppressing default instantiation of the target bean.
	 * <p>If a non-null object is returned by this method, the bean creation process
	 * will be short-circuited. The only further processing applied is the
	 * {@link #postProcessAfterInitialization} callback from the configured
	 * {@link BeanPostProcessor BeanPostProcessors}.
	 * <p>This callback will be applied to bean definitions with their bean class,
	 * as well as to factory-method definitions in which case the returned bean type
	 * will be passed in here.
	 * <p>Post-processors may implement the extended
	 * {@link SmartInstantiationAwareBeanPostProcessor} interface in order
	 * to predict the type of the bean object that they are going to return here.
	 * <p>The default implementation returns {@code null}.
	 * @param beanClass the class of the bean to be instantiated
	 * @param beanName the name of the bean
	 * @return the bean object to expose instead of a default instance of the target bean,
	 * or {@code null} to proceed with default instantiation
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see #postProcessAfterInstantiation
	 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getBeanClass()
	 * @see org.springframework.beans.factory.support.AbstractBeanDefinition#getFactoryMethodName()
	 */
	default @Nullable Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) throws BeansException {
		return null;
	}

	/**
	 * 该方法在构造器或工厂方法创建的bean后，但在属性被填充前被调用。
	 * 返回true表示继续填充属性，返回false则不填充属性。
	 * 即该方法可以控制要不要填充属性，如果不填充。则表示bean在半成品时生命就结束了。
	 * 该方法相对温和，我们可以进行一些判断是否符合预期，决定要不要填充属性以完成bean的后续生命周期操作。
	 * 不建议使用该方法，可以使用下面postProcessProperties方法代替。不过postProcessProperties方法
	 * 是Spring5.1引入的，之前版本也只能使用postProcessBeforeInstantiation方法。
	 */
	/**
	 * Perform operations after the bean has been instantiated, via a constructor or factory method,
	 * but before Spring property population (from explicit properties or autowiring) occurs.
	 * <p>This is the ideal callback for performing custom field injection on the given bean
	 * instance, right before Spring's autowiring kicks in.
	 * <p>The default implementation returns {@code true}.
	 * @param bean the bean instance created, with properties not having been set yet
	 * @param beanName the name of the bean
	 * @return {@code true} if properties should be set on the bean; {@code false}
	 * if property population should be skipped. Normal implementations should return {@code true}.
	 * Returning {@code false} will also prevent any subsequent InstantiationAwareBeanPostProcessor
	 * instances being invoked on this bean instance.
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @see #postProcessBeforeInstantiation
	 */
	default boolean postProcessAfterInstantiation(Object bean, String beanName) throws BeansException {
		return true;
	}


	/**
	 * 在工厂将属性值应用于给定的bean之前，对属性值进行一些处理。
	 * 该方法比较灵活，可以决定哪些属性可以填充哪些不能，哪些需要修改等等。
	 *
	 * @param pvs 工厂即将应用的属性值（永远不会为null），默认直接返回pvs
	 * @param bean 已创建但尚未设置属性的bean实例
	 * @param beanName bean的名称
	 * @return 要应用于给定bean的实际属性值（可以是传入的PropertyValues实例），或null以跳过属性填充
	 */
	/**
	 * Post-process the given property values before the factory applies them
	 * to the given bean.
	 * <p>The default implementation returns the given {@code pvs} as-is.
	 * @param pvs the property values that the factory is about to apply (never {@code null})
	 * @param bean the bean instance created, but whose properties have not yet been set
	 * @param beanName the name of the bean
	 * @return the actual property values to apply to the given bean (can be the passed-in
	 * PropertyValues instance), or {@code null} to skip property population
	 * @throws org.springframework.beans.BeansException in case of errors
	 * @since 5.1
	 */
	default @Nullable PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName)
			throws BeansException {

		return pvs;
	}

}

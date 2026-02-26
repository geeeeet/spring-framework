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

package org.springframework.beans.factory.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;

/**
 * Support base class for singleton registries which need to handle
 * {@link org.springframework.beans.factory.FactoryBean} instances,
 * integrated with {@link DefaultSingletonBeanRegistry}'s singleton management.
 *
 * <p>Serves as base class for {@link AbstractBeanFactory}.
 *
 * @author Juergen Hoeller
 * @since 2.5.1
 */
public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {

	// 用来缓存由FactoryBean创建的单例对象：key是创建的bean的名称，value是创建的bean对象。
	// 这是专门针对FactoryBean创建的单例对象做的二级缓存
	/** Cache of singleton objects created by FactoryBeans: FactoryBean name to object. */
	private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);


	/**
	 * Determine the type for the given FactoryBean.
	 * @param factoryBean the FactoryBean instance to check
	 * @return the FactoryBean's object type,
	 * or {@code null} if the type cannot be determined yet
	 */
	protected @Nullable Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
		try {
			return factoryBean.getObjectType();
		}
		catch (Throwable ex) {
			// Thrown from the FactoryBean's getObjectType implementation.
			logger.info("FactoryBean threw exception from getObjectType, despite the contract saying " +
					"that it should return null if the type of its object cannot be determined yet", ex);
			return null;
		}
	}

	/**
	 * Determine the bean type for a FactoryBean by inspecting its attributes for a
	 * {@link FactoryBean#OBJECT_TYPE_ATTRIBUTE} value.
	 * @param attributes the attributes to inspect
	 * @return a {@link ResolvableType} extracted from the attributes or
	 * {@code ResolvableType.NONE}
	 * @since 5.2
	 */
	ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
		Object attribute = attributes.getAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE);
		if (attribute == null) {
			return ResolvableType.NONE;
		}
		if (attribute instanceof ResolvableType resolvableType) {
			return resolvableType;
		}
		if (attribute instanceof Class<?> clazz) {
			return ResolvableType.forClass(clazz);
		}
		throw new IllegalArgumentException("Invalid value type for attribute '" +
				FactoryBean.OBJECT_TYPE_ATTRIBUTE + "': " + attribute.getClass().getName());
	}

	/**
	 * Determine the FactoryBean object type from the given generic declaration.
	 * @param type the FactoryBean type
	 * @return the nested object type, or {@code NONE} if not resolvable
	 */
	ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
		return (type != null ? type.as(FactoryBean.class).getGeneric() : ResolvableType.NONE);
	}

	// 从缓存factoryBeanObjectCache中获取FactoryBean创建的bean对象
	/**
	 * Obtain an object to expose from the given FactoryBean, if available
	 * in cached form. Quick check for minimal synchronization.
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean,
	 * or {@code null} if not available
	 */
	protected @Nullable Object getCachedObjectForFactoryBean(String beanName) {
		return this.factoryBeanObjectCache.get(beanName);
	}

	/**
	 * 从FactoryBean获取它创建的单例对象
	 * @param factory FactoryBean实例
	 * @param beanName bean的名称
	 * @param shouldPostProcess 该bean是否需要后置处理
	 */
	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @param shouldPostProcess whether the bean is subject to post-processing
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	protected Object getObjectFromFactoryBean(FactoryBean<?> factory, @Nullable Class<?> requiredType,
			String beanName, boolean shouldPostProcess) {

		// 是单例bean并且一级缓存中存在该bean对象
		if (factory.isSingleton() && containsSingleton(beanName)) {
			Boolean lockFlag = isCurrentThreadAllowedToHoldSingletonLock();
			boolean locked;
			if (lockFlag == null) {
				this.singletonLock.lock();
				locked = true;
			}
			else {
				locked = (lockFlag && this.singletonLock.tryLock());
			}
			try {
				// SmartFactoryBean 被认为线程安全 → 不需要 synchronized(factory)
				if (factory instanceof SmartFactoryBean<?>) {
					// A SmartFactoryBean may return multiple object types -> do not cache.
					// Also, a SmartFactoryBean needs to be thread-safe -> no synchronization necessary.
					// 获取FactoryBean创建的单例对象
					Object object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
					if (shouldPostProcess) { // 是否需要做后置处理，如 AOP 代理、Aware 接口回调等
						object = postProcessObjectFromSingletonFactoryBean(object, beanName, locked);
					}
					return object;
				}
				else {
					// Defensively synchronize against non-thread-safe FactoryBean.getObject() implementations,
					// potentially to be called from a background thread while the main thread currently calls
					// the same getObject() method within the singleton lock.
					// 普通 FactoryBean（非 Smart，可能不线程安全）
					synchronized (factory) { // 对 factory 对象本身加锁（对象监视器）
						Object object = this.factoryBeanObjectCache.get(beanName);
						if (object == null) {
							object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
							// Only post-process and store if not put there already during getObject() call above
							// (for example, because of circular reference processing triggered by custom getBean calls)
							// Double-Check：防止递归调用 getBean 导致重复 put
							Object alreadyThere = this.factoryBeanObjectCache.get(beanName);
							if (alreadyThere != null) {
								object = alreadyThere;
							}
							else {
								if (shouldPostProcess) {
									object = postProcessObjectFromSingletonFactoryBean(object, beanName, locked);
								}
								if (containsSingleton(beanName)) {
									this.factoryBeanObjectCache.put(beanName, object);
								}
							}
						}
						return object;
					}
				}
			}
			finally {
				if (locked) {
					this.singletonLock.unlock();
				}
			}
		}
		// 不是单例bean，直接调用getObject()获取
		else {
			Object object = doGetObjectFromFactoryBean(factory, requiredType, beanName);
			if (shouldPostProcess) {
				try {
					object = postProcessObjectFromFactoryBean(object, beanName);
				}
				catch (Throwable ex) {
					throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
				}
			}
			return object;
		}
	}

	// 从FactoryBean获取它创建的对象
	/**
	 * Obtain an object to expose from the given FactoryBean.
	 * @param factory the FactoryBean instance
	 * @param beanName the name of the bean
	 * @return the object obtained from the FactoryBean
	 * @throws BeanCreationException if FactoryBean object creation failed
	 * @see org.springframework.beans.factory.FactoryBean#getObject()
	 */
	private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, @Nullable Class<?> requiredType, String beanName)
			throws BeanCreationException {

		Object object;
		try {
			// 调用getObject()获取FactoryBean创建的对象
			object = (requiredType != null && factory instanceof SmartFactoryBean<?> smartFactoryBean ?
					smartFactoryBean.getObject(requiredType) : factory.getObject());
		}
		catch (FactoryBeanNotInitializedException ex) {
			throw new BeanCurrentlyInCreationException(beanName, ex.toString());
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
		}

		// Do not accept a null value for a FactoryBean that's not fully
		// initialized yet: Many FactoryBeans just return null then.
		if (object == null) {
			// 为空后判断是否bean正在创建，是则抛出异常，否则返回NullBean
			if (isSingletonCurrentlyInCreation(beanName)) {
				throw new BeanCurrentlyInCreationException(
						beanName, "FactoryBean which is currently in creation returned null from getObject");
			}
			object = new NullBean();
		}
		return object;
	}

	/**
	 * Post-process the given object instance produced by a singleton FactoryBean.
	 */
	private Object postProcessObjectFromSingletonFactoryBean(Object object, String beanName, boolean locked) {
		if (locked) {
			if (isSingletonCurrentlyInCreation(beanName)) {
				// Temporarily return non-post-processed object, not storing it yet
				return object;
			}
			beforeSingletonCreation(beanName);
		}
		try {
			return postProcessObjectFromFactoryBean(object, beanName);
		}
		catch (Throwable ex) {
			throw new BeanCreationException(beanName,
					"Post-processing of FactoryBean's singleton object failed", ex);
		}
		finally {
			if (locked) {
				afterSingletonCreation(beanName);
			}
		}
	}

	/**
	 * Post-process the given object that has been obtained from the FactoryBean.
	 * The resulting object will get exposed for bean references.
	 * <p>The default implementation simply returns the given object as-is.
	 * Subclasses may override this, for example, to apply post-processors.
	 * @param object the object obtained from the FactoryBean.
	 * @param beanName the name of the bean
	 * @return the object to expose
	 * @throws org.springframework.beans.BeansException if any post-processing failed
	 */
	protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
		return object;
	}

	/**
	 * Get a FactoryBean for the given bean if possible.
	 * @param beanName the name of the bean
	 * @param beanInstance the corresponding bean instance
	 * @return the bean instance as FactoryBean
	 * @throws BeansException if the given bean cannot be exposed as a FactoryBean
	 */
	protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
		if (!(beanInstance instanceof FactoryBean<?> factoryBean)) {
			throw new BeanCreationException(beanName,
					"Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
		}
		return factoryBean;
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void removeSingleton(String beanName) {
		super.removeSingleton(beanName);
		this.factoryBeanObjectCache.remove(beanName);
	}

	/**
	 * Overridden to clear the FactoryBean object cache as well.
	 */
	@Override
	protected void clearSingletonCache() {
		super.clearSingletonCache();
		this.factoryBeanObjectCache.clear();
	}

}

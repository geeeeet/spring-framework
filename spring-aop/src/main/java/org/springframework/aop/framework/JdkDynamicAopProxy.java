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

package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.aop.AopInvocationException;
import org.springframework.aop.RawTargetAccess;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.KotlinDetector;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * JDK-based {@link AopProxy} implementation for the Spring AOP framework,
 * based on JDK {@link java.lang.reflect.Proxy dynamic proxies}.
 *
 * <p>Creates a dynamic proxy, implementing the interfaces exposed by
 * the AopProxy. Dynamic proxies <i>cannot</i> be used to proxy methods
 * defined in classes, rather than interfaces.
 *
 * <p>Objects of this type should be obtained through proxy factories,
 * configured by an {@link AdvisedSupport} class. This class is internal
 * to Spring's AOP framework and need not be used directly by client code.
 *
 * <p>Proxies created using this class will be thread-safe if the
 * underlying (target) class is thread-safe.
 *
 * <p>Proxies are serializable so long as all Advisors (including Advices
 * and Pointcuts) and the TargetSource are serializable.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @author Dave Syer
 * @author Sergey Tsypanov
 * @author Sebastien Deleuze
 * @see java.lang.reflect.Proxy
 * @see AdvisedSupport
 * @see ProxyFactory
 */
final class JdkDynamicAopProxy implements AopProxy, InvocationHandler, Serializable {

	/** use serialVersionUID from Spring 1.2 for interoperability. */
	private static final long serialVersionUID = 5531744639992436476L;


	private static final String COROUTINES_FLOW_CLASS_NAME = "kotlinx.coroutines.flow.Flow";

	private static final boolean COROUTINES_REACTOR_PRESENT = ClassUtils.isPresent(
			"kotlinx.coroutines.reactor.MonoKt", JdkDynamicAopProxy.class.getClassLoader());

	/** We use a static Log to avoid serialization issues. */
	private static final Log logger = LogFactory.getLog(JdkDynamicAopProxy.class);

	/**
	 * 第一个重要成员变量
	 * AOP 配置的大管家。里面装着：你写的切面、通知（@Before、@After等）
	 * 目标对象（真正干活的那个对象）
	 * 要不要暴露代理等配置
	 */
	/** Config used to configure this proxy. */
	private final AdvisedSupport advised;

	/**
	 * 第二个重要成员变量
	 * 一个缓存，存着“需要代理哪些接口”、“equals 和 hashCode 方法有没有在接口里定义过”等信息，避免每次都重复计算，性能更好。
	 */
	/** Cached in {@link AdvisedSupport#proxyMetadataCache}. */
	private transient ProxiedInterfacesCache cache;


	/**
	 * Construct a new JdkDynamicAopProxy for the given AOP configuration.
	 * @param config the AOP configuration as AdvisedSupport object
	 * @throws AopConfigException if the config is invalid. We try to throw an informative
	 * exception in this case, rather than let a mysterious failure happen later.
	 */
	public JdkDynamicAopProxy(AdvisedSupport config) throws AopConfigException {
		Assert.notNull(config, "AdvisedSupport must not be null");
		this.advised = config;

		// Initialize ProxiedInterfacesCache if not cached already
		ProxiedInterfacesCache cache;
		if (config.proxyMetadataCache instanceof ProxiedInterfacesCache proxiedInterfacesCache) {
			cache = proxiedInterfacesCache;
		}
		else {
			cache = new ProxiedInterfacesCache(config);
			config.proxyMetadataCache = cache;
		}
		this.cache = cache;
	}


	/**
	 * 创建代理对象的方法
	 *
	 * @return
	 */
	@Override
	public Object getProxy() {
		return getProxy(ClassUtils.getDefaultClassLoader());
	}

	@Override
	public Object getProxy(@Nullable ClassLoader classLoader) {
		if (logger.isTraceEnabled()) {
			logger.trace("Creating JDK dynamic proxy: " + this.advised.getTargetSource());
		}
		return Proxy.newProxyInstance(determineClassLoader(classLoader), this.cache.proxiedInterfaces, this);
	}

	@SuppressWarnings("deprecation")
	@Override
	public Class<?> getProxyClass(@Nullable ClassLoader classLoader) {
		return Proxy.getProxyClass(determineClassLoader(classLoader), this.cache.proxiedInterfaces);
	}

	/**
	 * Determine whether the JDK bootstrap or platform loader has been suggested ->
	 * use higher-level loader which can see Spring infrastructure classes instead.
	 */
	private ClassLoader determineClassLoader(@Nullable ClassLoader classLoader) {
		if (classLoader == null) {
			// JDK bootstrap loader -> use spring-aop ClassLoader instead.
			return getClass().getClassLoader();
		}
		if (classLoader.getParent() == null) {
			// Potentially the JDK platform loader on JDK 9+
			ClassLoader aopClassLoader = getClass().getClassLoader();
			ClassLoader aopParent = aopClassLoader.getParent();
			while (aopParent != null) {
				if (classLoader == aopParent) {
					// Suggested ClassLoader is ancestor of spring-aop ClassLoader
					// -> use spring-aop ClassLoader itself instead.
					return aopClassLoader;
				}
				aopParent = aopParent.getParent();
			}
		}
		// Regular case: use suggested ClassLoader as-is.
		return classLoader;
	}


	/**
	 * 代理里面最重要的方法，所有对代理对象的方法调用都会被这个方法拦截到。
	 */
	/**
	 * Implementation of {@code InvocationHandler.invoke}.
	 * <p>Callers will see exactly the exception thrown by the target,
	 * unless a hook method throws an exception.
	 */
	@Override
	public @Nullable Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
		Object oldProxy = null;
		boolean setProxyContext = false;

		TargetSource targetSource = this.advised.targetSource;
		Object target = null;

		try {
			// 阶段1：特殊方法快速处理。避免代理对象和目标对象 equals()、hashCode() 不一致导致的问题，同时支持 ((Advised) proxy).getAdvisors() 等操作。
			if (!this.cache.equalsDefined && AopUtils.isEqualsMethod(method)) {
				// The target does not implement the equals(Object) method itself.
				return equals(args[0]);
			}
			else if (!this.cache.hashCodeDefined && AopUtils.isHashCodeMethod(method)) {
				// The target does not implement the hashCode() method itself.
				return hashCode();
			}
			else if (method.getDeclaringClass() == DecoratingProxy.class) {
				// There is only getDecoratedClass() declared -> dispatch to proxy config.
				return AopProxyUtils.ultimateTargetClass(this.advised);
			}
			else if (!this.advised.isOpaque() && method.getDeclaringClass().isInterface() &&
					method.getDeclaringClass().isAssignableFrom(Advised.class)) {
				// Service invocations on ProxyConfig with the proxy config...
				return AopUtils.invokeJoinpointUsingReflection(this.advised, method, args);
			}

			Object retVal;

			// 阶段2：这是解决内部自调用（this.method() 不走 AOP）的关键机制。开启后可在目标方法中通过 AopContext.currentProxy() 获取当前代理对象。
			if (this.advised.isExposeProxy()) {
				// Make invocation available if necessary.
				oldProxy = AopContext.setCurrentProxy(proxy);
				setProxyContext = true;
			}

			// 阶段 3：获取目标对象 + 拦截器链
			// Get as late as possible to minimize the time we "own" the target,
			// in case it comes from a pool.
			target = targetSource.getTarget();
			Class<?> targetClass = (target != null ? target.getClass() : null);

			// Get the interception chain for this method.
			List<Object> chain = this.advised.getInterceptorsAndDynamicInterceptionAdvice(method, targetClass);

			// 阶段 4：真正执行
			// Check whether we have any advice. If we don't, we can fall back on direct
			// reflective invocation of the target, and avoid creating a MethodInvocation.
			if (chain.isEmpty()) {
				// 没有通知 → 直接反射调用（最高性能）
				// We can skip creating a MethodInvocation: just invoke the target directly
				// Note that the final invoker must be an InvokerInterceptor so we know it does
				// nothing but a reflective operation on the target, and no hot swapping or fancy proxying.
				@Nullable Object[] argsToUse = AopProxyUtils.adaptArgumentsIfNecessary(method, args);
				retVal = AopUtils.invokeJoinpointUsingReflection(target, method, argsToUse);
			}
			else {
				// 有通知 → 责任链模式
				// We need to create a method invocation...
				MethodInvocation invocation =
						new ReflectiveMethodInvocation(proxy, target, method, args, targetClass, chain);
				// Proceed to the joinpoint through the interceptor chain.
				retVal = invocation.proceed();
			}

			//阶段 5：返回值特殊处理
			// 如果目标方法返回 this，则返回代理对象（保持一致性）
			// Massage return value if necessary.
			Class<?> returnType = method.getReturnType();
			if (retVal != null && retVal == target &&
					returnType != Object.class && returnType.isInstance(proxy) &&
					!RawTargetAccess.class.isAssignableFrom(method.getDeclaringClass())) {
				// Special case: it returned "this" and the return type of the method
				// is type-compatible. Note that we can't help if the target sets
				// a reference to itself in another returned object.
				retVal = proxy;
			}
			// 基本类型返回 null 时抛出异常（符合 Java 语义）
			else if (retVal == null && returnType != void.class && returnType.isPrimitive()) {
				throw new AopInvocationException(
						"Null return value from advice does not match primitive return type for: " + method);
			}

			// 阶段 6：Kotlin 协程支持（Spring 6+ 新增）
			if (COROUTINES_REACTOR_PRESENT && KotlinDetector.isSuspendingFunction(method)) {
				return COROUTINES_FLOW_CLASS_NAME.equals(new MethodParameter(method, -1).getParameterType().getName()) ?
						CoroutinesUtils.asFlow(retVal) : CoroutinesUtils.awaitSingleOrNull(retVal, args[args.length - 1]);
			}
			return retVal;
		}
		finally {
			if (target != null && !targetSource.isStatic()) {
				// Must have come from TargetSource.
				targetSource.releaseTarget(target);
			}
			if (setProxyContext) {
				// Restore old proxy.
				AopContext.setCurrentProxy(oldProxy);
			}
		}
	}


	/**
	 * Equality means interfaces, advisors and TargetSource are equal.
	 * <p>The compared object may be a JdkDynamicAopProxy instance itself
	 * or a dynamic proxy wrapping a JdkDynamicAopProxy instance.
	 */
	@Override
	public boolean equals(@Nullable Object other) {
		if (other == this) {
			return true;
		}
		if (other == null) {
			return false;
		}

		JdkDynamicAopProxy otherProxy;
		if (other instanceof JdkDynamicAopProxy jdkDynamicAopProxy) {
			otherProxy = jdkDynamicAopProxy;
		}
		else if (Proxy.isProxyClass(other.getClass())) {
			InvocationHandler ih = Proxy.getInvocationHandler(other);
			if (!(ih instanceof JdkDynamicAopProxy jdkDynamicAopProxy)) {
				return false;
			}
			otherProxy = jdkDynamicAopProxy;
		}
		else {
			// Not a valid comparison...
			return false;
		}

		// If we get here, otherProxy is the other AopProxy.
		return AopProxyUtils.equalsInProxy(this.advised, otherProxy.advised);
	}

	/**
	 * Proxy uses the hash code of the TargetSource.
	 */
	@Override
	public int hashCode() {
		return JdkDynamicAopProxy.class.hashCode() * 13 + this.advised.getTargetSource().hashCode();
	}


	//---------------------------------------------------------------------
	// Serialization support
	//---------------------------------------------------------------------

	private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
		// Rely on default serialization; just initialize state after deserialization.
		ois.defaultReadObject();

		// Initialize transient fields.
		this.cache = new ProxiedInterfacesCache(this.advised);
	}


	/**
	 * Holder for the complete proxied interfaces and derived metadata,
	 * to be cached in {@link AdvisedSupport#proxyMetadataCache}.
	 * @since 6.1.3
	 */
	private static final class ProxiedInterfacesCache {

		final Class<?>[] proxiedInterfaces;

		final boolean equalsDefined;

		final boolean hashCodeDefined;

		ProxiedInterfacesCache(AdvisedSupport config) {
			this.proxiedInterfaces = AopProxyUtils.completeProxiedInterfaces(config, true);

			// Find any {@link #equals} or {@link #hashCode} method that may be defined
			// on the supplied set of interfaces.
			boolean equalsDefined = false;
			boolean hashCodeDefined = false;
			for (Class<?> proxiedInterface : this.proxiedInterfaces) {
				Method[] methods = proxiedInterface.getDeclaredMethods();
				for (Method method : methods) {
					if (AopUtils.isEqualsMethod(method)) {
						equalsDefined = true;
						if (hashCodeDefined) {
							break;
						}
					}
					if (AopUtils.isHashCodeMethod(method)) {
						hashCodeDefined = true;
						if (equalsDefined) {
							break;
						}
					}
				}
				if (equalsDefined && hashCodeDefined) {
					break;
				}
			}
			this.equalsDefined = equalsDefined;
			this.hashCodeDefined = hashCodeDefined;
		}
	}

}

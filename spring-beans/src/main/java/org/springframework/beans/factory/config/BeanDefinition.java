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

import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;

/**
 * 这是一个定义bean的接口，非常重要，它描述了一个bean有哪些属性值、构造函数参数值以及其他信息。
 * 这个接口的主要作用是封装bean的元数据，比如bean的属性值、构造函数参数值以及其他信息。
 * bean的创建根据这里的描述来，后续也好让BeanFactoryPostProcessor进行修改。
 */

/**
 * A BeanDefinition describes a bean instance, which has property values,
 * constructor argument values, and further information supplied by
 * concrete implementations.
 *
 * <p>This is just a minimal interface: The main intention is to allow a
 * {@link BeanFactoryPostProcessor} to introspect and modify property values
 * and other bean metadata.
 *
 * @author Juergen Hoeller
 * @author Rob Harrop
 * @since 19.03.2004
 * @see ConfigurableListableBeanFactory#getBeanDefinition
 * @see org.springframework.beans.factory.support.RootBeanDefinition
 * @see org.springframework.beans.factory.support.ChildBeanDefinition
 */
public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {

	// bean的范围：singleton 单例
	/**
	 * Scope identifier for the standard singleton scope: {@value}.
	 * <p>Note that extended bean factories might support further scopes.
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_SINGLETON
	 */
	String SCOPE_SINGLETON = ConfigurableBeanFactory.SCOPE_SINGLETON;

	// bean的范围：prototype 原型
	/**
	 * Scope identifier for the standard prototype scope: {@value}.
	 * <p>Note that extended bean factories might support further scopes.
	 * @see #setScope
	 * @see ConfigurableBeanFactory#SCOPE_PROTOTYPE
	 */
	String SCOPE_PROTOTYPE = ConfigurableBeanFactory.SCOPE_PROTOTYPE;


	// ROLE_APPLICATION = 0 表示这个 Bean 是应用程序的主要组成部分，通常是由用户自定义的 Bean。
	// 这种角色提示可以帮助开发者或工具在分析配置时快速区分不同类型的 Bean。
	/**
	 * Role hint indicating that a {@code BeanDefinition} is a major part
	 * of the application. Typically corresponds to a user-defined bean.
	 */
	int ROLE_APPLICATION = 0;

	// 表示支持性组件，是辅助性的 Bean,通常是ComponentDefinition的一部分。
	/**
	 * Role hint indicating that a {@code BeanDefinition} is a supporting
	 * part of some larger configuration, typically an outer
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 * {@code SUPPORT} beans are considered important enough to be aware
	 * of when looking more closely at a particular
	 * {@link org.springframework.beans.factory.parsing.ComponentDefinition},
	 * but not when looking at the overall configuration of an application.
	 */
	int ROLE_SUPPORT = 1;

	// 表示基础设施级别的 Bean，与用户业务逻辑无关。
	/**
	 * Role hint indicating that a {@code BeanDefinition} is providing an
	 * entirely background role and has no relevance to the end-user. This hint is
	 * used when registering beans that are completely part of the internal workings
	 * of a {@link org.springframework.beans.factory.parsing.ComponentDefinition}.
	 */
	int ROLE_INFRASTRUCTURE = 2;


	// Modifiable attributes

	// 设置定义该bean的父定义名称，如果有的话。
	/**
	 * Set the name of the parent definition of this bean definition, if any.
	 */
	void setParentName(@Nullable String parentName);

	// 获取定义该bean的父定义的名称，如果有的话。
	/**
	 * Return the name of the parent definition of this bean definition, if any.
	 */
	@Nullable String getParentName();

	/**
	 * 用途：
	 * 用于指定 Bean 的具体实现类。
	 * 在后置处理中可以动态修改类名，例如通过代理类替换原始类。
	 *
	 * 关联方法：
	 * setParentName：设置父 Bean 定义名称。
	 * setFactoryBeanName：设置工厂 Bean 名称。
	 * setFactoryMethodName：设置工厂方法名称。
	 *
	 * 典型应用场景
	 * 在 XML 配置或注解配置中指定 Bean 的实现类。
	 * 在后置处理器中动态替换 Bean 的实现类（例如 AOP 代理）。
	 */
	/**
	 * Specify the bean class name of this bean definition.
	 * <p>The class name can be modified during bean factory post-processing,
	 * typically replacing the original class name with a parsed variant of it.
	 * @see #setParentName
	 * @see #setFactoryBeanName
	 * @see #setFactoryMethodName
	 */
	void setBeanClassName(@Nullable String beanClassName);

	// 获取bean实现类的类名
	/**
	 * Return the current bean class name of this bean definition.
	 * <p>Note that this does not have to be the actual class name used at runtime, in
	 * case of a child definition overriding/inheriting the class name from its parent.
	 * Also, this may just be the class that a factory method is called on, or it may
	 * even be empty in case of a factory bean reference that a method is called on.
	 * Hence, do <i>not</i> consider this to be the definitive bean type at runtime but
	 * rather only use it for parsing purposes at the individual bean definition level.
	 * @see #getParentName()
	 * @see #getFactoryBeanName()
	 * @see #getFactoryMethodName()
	 */
	@Nullable String getBeanClassName();

	/**
	 * 设置该bean的作用域，系统内置6种作用域，其中两种在当前类定义，也可以自定义作用域。
	 * Scope 名称（字符串） 是否内置    含义/行为                                    如何启用/注册
	 * "singleton"       是      容器中唯一实例（默认）                             无需额外配置
	 * "prototype"       是      每次获取都创建新实例                               无需额外配置
	 * "request"         是      每个 HTTP 请求一个实例                            在 Web 环境（WebApplicationContext）自动支持
	 * "session"         是      每个 HTTP Session 一个实例                        在 Web 环境自动支持
	 * "application"     是      整个 ServletContext 一个实例（全局）                在 Web 环境自动支持
	 * "websocket"       是      WebSocket 会话级别                               需要 WebSocket 支持
	 * 自定义 scope       否      自定义作用域（例如线程作用域、定时任务作用域等）        需要手动注册 CustomScopeConfigurer 或 ScopeRegistry
	 * （如 "thread"）
	 *
	 * 自定义scope两种方式
	 * // 1. 先定义一个自定义 Scope 实现
	 * public class ThreadScope implements Scope {
	 *     private final ThreadLocal<Map<String, Object>> threadScope = new ThreadLocal<>() {
	 *         @Override
	 *         protected Map<String, Object> initialValue() {
	 *             return new HashMap<>();
	 *         }
	 *     };
	 *
	 *     @Override
	 *     public Object get(String name, ObjectFactory<?> objectFactory) {
	 *         Map<String, Object> scope = threadScope.get();
	 *         return scope.computeIfAbsent(name, k -> objectFactory.getObject());
	 *     }
	 *
	 *     @Override
	 *     public Object remove(String name) {
	 *         Map<String, Object> scope = threadScope.get();
	 *         return scope.remove(name);
	 *     }
	 *
	 *     @Override
	 *     public void registerDestructionCallback(String name, Runnable callback) {
	 *         // 可选实现销毁回调
	 *     }
	 *
	 *     @Override
	 *     public Object resolveContextualObject(String key) {
	 *         return null;
	 *     }
	 *
	 *     @Override
	 *     public String getConversationId() {
	 *         return Thread.currentThread().getName();
	 *     }
	 * }
	 *
	 * // 示例1：在 ApplicationContext 初始化后手动注册
	 * ConfigurableApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
	 * ConfigurableBeanFactory beanFactory = context.getBeanFactory();
	 * beanFactory.registerScope("thread", new ThreadScope());  // 这里就是注册
	 *
	 * // 示例2：在 @Configuration 中使用 CustomScopeConfigurer
	 * @Configuration
	 * public class AppConfig {
	 *
	 *     @Bean
	 *     public static CustomScopeConfigurer customScopeConfigurer() {
	 *         CustomScopeConfigurer configurer = new CustomScopeConfigurer();
	 *
	 *         Map<String, Object> scopes = new HashMap<>();
	 *         scopes.put("thread", new ThreadScope());           // 自定义 Scope
	 *         scopes.put("another", new AnotherCustomScope());
	 *
	 *         configurer.setScopes(scopes);
	 *         return configurer;
	 *     }
	 *
	 *     @Bean
	 *     @Scope("thread") // 现在就可以用了
	 *     public SomeThreadLocalService threadLocalService() {
	 *         return new SomeThreadLocalService();
	 *     }
	 * }
	 */
	/**
	 * Override the target scope of this bean, specifying a new scope name.
	 * @see #SCOPE_SINGLETON
	 * @see #SCOPE_PROTOTYPE
	 */
	void setScope(@Nullable String scope);

	/**
	 * Return the name of the current target scope for this bean,
	 * or {@code null} if not known yet.
	 */
	@Nullable String getScope();

	// 设置bean是否懒加载，默认是false，即在容器启动时就创建该bean实例。否则在第一次调用该bean时才创建该bean实例。
	/**
	 * Set whether this bean should be lazily initialized.
	 * <p>If {@code false}, the bean will get instantiated on startup by bean
	 * factories that perform eager initialization of singletons.
	 */
	void setLazyInit(boolean lazyInit);

	/**
	 * Return whether this bean should be lazily initialized, i.e. not
	 * eagerly instantiated on startup. Only applicable to a singleton bean.
	 */
	boolean isLazyInit();

	// setDependsOn 的作用：在 BeanDefinition 上声明当前 Bean 依赖的其他 Bean 名称，
	// 容器会保证这些被依赖的 Bean 在当前 Bean 初始化前先被初始化。
	// 注意是到初始化阶段，而不仅仅是实例化阶段。
	/**
	 * Set the names of the beans that this bean depends on being initialized.
	 * The bean factory will guarantee that these beans get initialized first.
	 * <p>Note that dependencies are normally expressed through bean properties or
	 * constructor arguments. This property should just be necessary for other kinds
	 * of dependencies like statics (*ugh*) or database preparation on startup.
	 */
	void setDependsOn(String @Nullable ... dependsOn);

	/**
	 * Return the bean names that this bean depends on.
	 */
	String @Nullable [] getDependsOn();


	/**
	 * 设置当前 Bean 是否可以被其他 Bean 通过类型（type-based）自动装配机制注入。
	 * 影响范围：仅影响基于类型的自动装配，不影响按名称的显式引用。
	 * 默认为 true，表示可以被其他 Bean 自动装配。
	 * 1、类型自动装配 vs 显式引用：
	 * 如果设置为 false，则该 Bean 不会被 Spring 的 @Autowired 或 XML 配置中的 byType 方式选中。
	 * 但如果其他 Bean 显式通过名称引用该 Bean（例如 <ref bean="xxx"/> 或 @Qualifier("xxx")），仍然可以成功注入。
	 * 2、典型应用场景：
	 * 当多个相同类型的 Bean 存在时，可以通过将某些 Bean 设置为非候选者（autowireCandidate = false）来避免歧义。
	 * 用于排除一些仅供内部使用的 Bean，防止它们被意外注入到其他组件中。
	 *
	 * 3、示例代码：
	 * @Configuration
	 * public class AppConfig {
	 *
	 *     @Bean
	 *     @Primary
	 *     public MyService primaryService() {
	 *         return new MyServiceImpl();
	 *     }
	 *
	 *     @Bean
	 *     public MyService secondaryService() {
	 *         return new MyServiceImpl();
	 *     }
	 *
	 *     // 将 secondaryService 排除出自动装配候选列表
	 *     @Bean
	 *     public BeanDefinition secondaryServiceDefinition() {
	 *         GenericBeanDefinition bd = new GenericBeanDefinition();
	 *         bd.setBeanClass(MyServiceImpl.class);
	 *         bd.setAutowireCandidate(false); // 关键设置
	 *         return bd;
	 *     }
	 * }
	 */
	/**
	 * Set whether this bean is a candidate for getting autowired into some other bean.
	 * <p>Note that this flag is designed to only affect type-based autowiring.
	 * It does not affect explicit references by name, which will get resolved even
	 * if the specified bean is not marked as an autowire candidate. As a consequence,
	 * autowiring by name will nevertheless inject a bean if the name matches.
	 */
	void setAutowireCandidate(boolean autowireCandidate);

	/**
	 * Return whether this bean is a candidate for getting autowired into some other bean.
	 */
	boolean isAutowireCandidate();

	/**
	 * 设置当前 Bean 是否是首选的 Bean。
	 * 当有多个匹配的 Bean 时，将选择首选的 Bean。
	 */
	/**
	 * Set whether this bean is a primary autowire candidate.
	 * <p>If this value is {@code true} for exactly one bean among multiple
	 * matching candidates, it will serve as a tie-breaker.
	 * @see #setFallback
	 */
	void setPrimary(boolean primary);

	/**
	 * Return whether this bean is a primary autowire candidate.
	 */
	boolean isPrimary();

	/**
	 * 功能： 设置当前 Bean 是否作为备用（fallback）自动装配候选者。
	 * 当多个匹配的 Bean 存在时，如果所有其他 Bean 都被标记为备用候选者（fallback = true），
	 * 则唯一未被标记为备用的 Bean 会被优先选择。
	 * 适用场景： 主要用于解决多个相同类型 Bean 的自动装配冲突问题，提供一种“兜底”机制。
	 * Spring 6.2新增功能。
	 *
	 * 类型：boolean
	 * 含义：
	 * true：表示该 Bean 是备用候选者。
	 * false（默认值）：表示该 Bean 不是备用候选者，具有更高的优先级。
	 *
	 * 典型应用场景：
	 * 当多个相同类型的 Bean 存在时，可以通过组合使用 setPrimary 和 setFallback 来精确控制自动装配的行为。
	 * 例如：
	 * 有一个默认实现（primary = true）。
	 * 多个备用实现（fallback = true）。
	 * 如果默认实现不可用，则从备用实现中选择一个。
	 *
	 * 示例代码：
	 * @Configuration
	 * public class AppConfig {
	 *
	 *     @Bean
	 *     @Primary
	 *     public MyService primaryService() {
	 *         return new DefaultMyServiceImpl();
	 *     }
	 *
	 *     @Bean
	 *     public MyService fallbackService1() {
	 *         MyServiceImpl service = new MyServiceImpl();
	 *         ((GenericBeanDefinition) service).setFallback(true); // 标记为备用
	 *         return service;
	 *     }
	 *
	 *     @Bean
	 *     public MyService fallbackService2() {
	 *         MyServiceImpl service = new MyServiceImpl();
	 *         ((GenericBeanDefinition) service).setFallback(true); // 标记为备用
	 *         return service;
	 *     }
	 * }
	 *
	 * 在上述例子中：
	 * primaryService 是首选 Bean。
	 * fallbackService1 和 fallbackService2 是备用 Bean。
	 * 如果 primaryService 不可用（例如被移除或禁用），Spring 会从 fallbackService1 和 fallbackService2 中选择一个。
	 */
	/**
	 * Set whether this bean is a fallback autowire candidate.
	 * <p>If this value is {@code true} for all beans but one among multiple
	 * matching candidates, the remaining bean will be selected.
	 * @since 6.2
	 * @see #setPrimary
	 */
	void setFallback(boolean fallback);

	/**
	 * Return whether this bean is a fallback autowire candidate.
	 * @since 6.2
	 */
	boolean isFallback();

	/**
	 * 指定用于创建当前 Bean 的工厂 Bean 的名称。
	 * 一般情况下不需要指定，默认为 null，表示使用默认的工厂 Bean。会根据beanclassname来决定使用哪个工厂 Bean。
	 * 典型应用场景：
	 * 创建复杂的对象实例（如第三方库的对象）。
	 * 需要动态决定 Bean 实例的创建逻辑。
	 */
	/**
	 * Specify the factory bean to use, if any.
	 * This is the name of the bean to call the specified factory method on.
	 * <p>A factory bean name is only necessary for instance-based factory methods.
	 * For static factory methods, the method will be derived from the bean class.
	 * @see #setFactoryMethodName
	 * @see #setBeanClassName
	 */
	void setFactoryBeanName(@Nullable String factoryBeanName);

	/**
	 * Return the factory bean name, if any.
	 * <p>This will be {@code null} for static factory methods which will
	 * be derived from the bean class instead.
	 * @see #getFactoryMethodName()
	 * @see #getBeanClassName()
	 */
	@Nullable String getFactoryBeanName();

	/**
	 * 指定用于创建当前 Bean 的工厂 Bean 的方法名称。
	 * 即使用哪个方法来创建当前bean
	 * 示例代码：
	 * @Configuration
	 * public class AppConfig {
	 *
	 *     // 定义一个工厂 Bean
	 *     @Bean
	 *     public MyFactory myFactory() {
	 *         return new MyFactory();
	 *     }
	 *
	 *     // 使用工厂 Bean 创建目标 Bean
	 *     @Bean
	 *     public MyService myService() {
	 *         GenericBeanDefinition bd = new GenericBeanDefinition();
	 *         bd.setFactoryBeanName("myFactory"); // 指定工厂 Bean 名称
	 *         bd.setFactoryMethodName("createService"); // 指定工厂方法名
	 *         return (MyService) bd;
	 *     }
	 * }
	 *
	 * // 工厂类
	 * class MyFactory {
	 *     public MyService createService() {
	 *         return new MyServiceImpl();
	 *     }
	 * }
	 */
	/**
	 * Specify a factory method, if any. This method will be invoked with
	 * constructor arguments, or with no arguments if none are specified.
	 * The method will be invoked on the specified factory bean, if any,
	 * or otherwise as a static method on the local bean class.
	 * @see #setFactoryBeanName
	 * @see #setBeanClassName
	 */
	void setFactoryMethodName(@Nullable String factoryMethodName);

	/**
	 * Return a factory method, if any.
	 * @see #getFactoryBeanName()
	 * @see #getBeanClassName()
	 */
	@Nullable String getFactoryMethodName();

	// 返回当前bean构造器的参数值，这个返回的ConstructorArgumentValues
	// 可以在bean创建时可以在后置处理中进行修改
	/**
	 * Return the constructor argument values for this bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * @return the ConstructorArgumentValues object (never {@code null})
	 */
	ConstructorArgumentValues getConstructorArgumentValues();

	// 判断当前bean是否定义了构造器的参数值
	/**
	 * Return if there are constructor argument values defined for this bean.
	 * @since 5.0.2
	 * @see #getConstructorArgumentValues()
	 */
	default boolean hasConstructorArgumentValues() {
		return !getConstructorArgumentValues().isEmpty();
	}

	/**
	 * 获取当前bean的属性值，这个返回的MutablePropertyValues可以在bean创建时可以在后置处理中进行修改
	 */
	/**
	 * Return the property values to be applied to a new instance of the bean.
	 * <p>The returned instance can be modified during bean factory post-processing.
	 * @return the MutablePropertyValues object (never {@code null})
	 */
	MutablePropertyValues getPropertyValues();

	/**
	 * Return if there are property values defined for this bean.
	 * @since 5.0.2
	 * @see #getPropertyValues()
	 */
	default boolean hasPropertyValues() {
		return !getPropertyValues().isEmpty();
	}

	// 设置初始化当前bean的方法名称
	/**
	 * Set the name of the initializer method.
	 * @since 5.1
	 */
	void setInitMethodName(@Nullable String initMethodName);

	/**
	 * Return the name of the initializer method.
	 * @since 5.1
	 */
	@Nullable String getInitMethodName();

	// 设置销毁当前bean的方法名称
	/**
	 * Set the name of the destroy method.
	 * @since 5.1
	 */
	void setDestroyMethodName(@Nullable String destroyMethodName);

	/**
	 * Return the name of the destroy method.
	 * @since 5.1
	 */
	@Nullable String getDestroyMethodName();

	/**
	 * Set the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * @since 5.1
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	void setRole(int role);

	/**
	 * Get the role hint for this {@code BeanDefinition}. The role hint
	 * provides the frameworks as well as tools an indication of
	 * the role and importance of a particular {@code BeanDefinition}.
	 * @see #ROLE_APPLICATION
	 * @see #ROLE_SUPPORT
	 * @see #ROLE_INFRASTRUCTURE
	 */
	int getRole();

	/**
	 * Set a human-readable description of this bean definition.
	 * @since 5.1
	 */
	void setDescription(@Nullable String description);

	/**
	 * Return a human-readable description of this bean definition.
	 */
	@Nullable String getDescription();


	// Read-only attributes

	/**
	 * 该方法用于返回当前 Bean 定义的可解析类型（ResolvableType），该类型基于 Bean 的类或其他特定元数据。
	 *
	 * 典型应用场景
	 * 在需要动态获取 Bean 类型信息的场景中非常有用，例如：
	 * 泛型注入时确定具体类型。
	 * 自定义 Bean 后置处理器中解析 Bean 的类型信息。
	 * 工具类或框架内部用于类型推断和校验。
	 *
	 * 示例代码
	 * 假设有一个泛型 Bean 定义：
	 * @Component
	 * public class MyGenericBean<T> {
	 *     private T value;
	 *     // getter/setter...
	 * }
	 *
	 * 在运行时可以通过 getResolvableType() 获取其具体泛型类型：
	 * ResolvableType type = beanDefinition.getResolvableType();
	 * System.out.println(type); // 输出类似: MyGenericBean<java.lang.String>
	 *
	 *
	 */
	/**
	 * Return a resolvable type for this bean definition,
	 * based on the bean class or other specific metadata.
	 * <p>This is typically fully resolved on a runtime-merged bean definition
	 * but not necessarily on a configuration-time definition instance.
	 * @return the resolvable type (potentially {@link ResolvableType#NONE})
	 * @since 5.2
	 * @see ConfigurableBeanFactory#getMergedBeanDefinition
	 */
	ResolvableType getResolvableType();

	// 返回该bean定义是否表示一个单例bean
	/**
	 * Return whether this a <b>Singleton</b>, with a single, shared instance
	 * returned on all calls.
	 * @see #SCOPE_SINGLETON
	 */
	boolean isSingleton();

	// 获取当前bean定义是否表示一个原型bean
	/**
	 * Return whether this a <b>Prototype</b>, with an independent instance
	 * returned for each call.
	 * @since 3.0
	 * @see #SCOPE_PROTOTYPE
	 */
	boolean isPrototype();

	/**
	 * 获取当前bean定义是否抽象的
	 * 作用：判断当前 Bean 是否是“抽象”的，即该 Bean 本身不会被实例化，而是作为其他具体子 Bean 定义的父模板。
	 * 返回值：
	 * true：表示该 Bean 是抽象的，不能直接实例化，只能作为父定义使用。
	 * false：表示该 Bean 是具体的，可以被实例化。
	 *
	 * 示例代码：
	 * // 抽象父 Bean 定义
	 * @Bean
	 * @Primary
	 * public abstract class AbstractService {
	 *     public void commonMethod() {
	 *         System.out.println("Common logic");
	 *     }
	 * }
	 *
	 * // 具体子 Bean 定义
	 * @Bean
	 * public class ConcreteService extends AbstractService {
	 *     public void specificMethod() {
	 *         System.out.println("Specific logic");
	 *     }
	 * }
	 *
	 * 在 XML 配置中可能是这样：
	 * <!-- 抽象父 Bean -->
	 * <bean id="abstractService" class="com.example.AbstractService" abstract="true">
	 *     <property name="commonProperty" value="sharedValue"/>
	 * </bean>
	 *
	 * <!-- 具体子 Bean -->
	 * <bean id="concreteService" parent="abstractService" class="com.example.ConcreteService">
	 *     <property name="specificProperty" value="uniqueValue"/>
	 * </bean>
	 */
	/**
	 * Return whether this bean is "abstract", that is, not meant to be instantiated
	 * itself but rather just serving as parent for concrete child bean definitions.
	 */
	boolean isAbstract();

	/**
	 * 返回当前bean定义的资源描述信息，例如 XML文件路径、Java 配置类名称等
	 * 用途：主要用于错误调试和日志记录，当出现配置错误时，可以通过该方法快速定位到具体的配置源。
	 */
	/**
	 * Return a description of the resource that this bean definition
	 * came from (for the purpose of showing context in case of errors).
	 */
	@Nullable String getResourceDescription();

	/**
	 * 返回当前 Bean 定义的原始来源定义（originating BeanDefinition），如果没有则返回 null。
	 * 该方法返回的是直接来源（immediate originator），而不是完整的来源链。
	 * 如果需要查找用户最初定义的 BeanDefinition，需遍历来源链（originator chain）。
	 *
	 * 示例代码：
	 * // 原始 Bean 定义
	 * @Bean
	 * public MyService rawService() {
	 *     return new MyServiceImpl();
	 * }
	 *
	 * // 装饰后的 Bean 定义
	 * @Bean
	 * public MyService decoratedService() {
	 *     return new DecoratedService(rawService());
	 * }
	 *
	 * 在这种情况下：
	 * decoratedService 的 getOriginatingBeanDefinition() 会返回 rawService 的定义。
	 * 如果继续调用 rawService 的 getOriginatingBeanDefinition()，可能会返回 null（因为它是最初的定义）。
	 */
	/**
	 * Return the originating BeanDefinition, or {@code null} if none.
	 * <p>Allows for retrieving the decorated bean definition, if any.
	 * <p>Note that this method returns the immediate originator. Iterate through the
	 * originator chain to find the original BeanDefinition as defined by the user.
	 */
	@Nullable BeanDefinition getOriginatingBeanDefinition();

}

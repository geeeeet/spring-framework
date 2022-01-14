package lrf.spring.autowire.config;


import lrf.spring.autowire.duty.DutyInter;
import lrf.spring.autowire.handle.Handleinter;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * java配置类
 *
 * @author lirufeng
 * @date 2019/4/15 22:26
 */

@Configuration
@ComponentScan(basePackageClasses = {DutyInter.class, Handleinter.class})
public class ConfigClass {
}

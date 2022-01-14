package lrf.spring.javaconfig.main;


import lrf.spring.javaconfig.config.ConfigClass;
import lrf.spring.javaconfig.handle.Handleinter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/**
 * TODO
 *
 * @author lirufeng
 * @date 2019/2/15 10:42
 */
public class MainClass {

    public static void main(String[] args) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(ConfigClass.class);
        Handleinter h = context.getBean(Handleinter.class);
        h.handleduty();
    }
}

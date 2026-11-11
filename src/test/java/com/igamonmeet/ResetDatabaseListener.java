package com.igamonmeet;

import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Every test class starts from a freshly migrated database (registered in META-INF/spring.factories).
 * Test classes share one Spring context and one in-memory database; without this, a Template published or a
 * Meeting created by one class changed what the next class saw, and failures depended on the run order.
 * Rebuilding the schema with Flyway takes well under a second on H2; the start-up upgrades run again after it.
 */
public class ResetDatabaseListener implements TestExecutionListener {

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        ApplicationContext ctx = testContext.getApplicationContext();
        if (ctx.getBeanNamesForType(Flyway.class).length == 0) {
            return;
        }
        Flyway flyway = ctx.getBean(Flyway.class);
        flyway.clean();
        flyway.migrate();
        List<ApplicationRunner> runners = new ArrayList<>(ctx.getBeansOfType(ApplicationRunner.class).values());
        AnnotationAwareOrderComparator.sort(runners);
        for (ApplicationRunner r : runners) {
            r.run(new DefaultApplicationArguments());
        }
    }
}

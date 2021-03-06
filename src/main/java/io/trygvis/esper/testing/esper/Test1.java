package io.trygvis.esper.testing.esper;

import com.espertech.esper.client.*;
import io.trygvis.esper.testing.*;
import org.slf4j.*;

import java.util.*;

import static java.lang.Thread.sleep;

public class Test1 {
    private static final String JDBC_URL = "jdbc:h2:mem:esper;DB_CLOSE_DELAY=-1";
//    private static final String JDBC_URL = "jdbc:h2:tcp://127.0.0.1/esper;DB_CLOSE_DELAY=-1";

    Logger logger;
    EPRuntime runtime;

    public static void main(String[] args) throws Exception {
        Config.loadFromDisk("test-1");
        new Test1().work();
    }

    private void work() throws Exception {
        logger = LoggerFactory.getLogger("app");

        Configuration config = new Configuration();

        ConfigurationDBRef configurationDBRef = new ConfigurationDBRef();
        configurationDBRef.setDriverManagerConnection("org.h2.Driver", JDBC_URL, "", "");
        configurationDBRef.setConnectionAutoCommit(false);
        config.addDatabaseReference("db1", configurationDBRef);
        config.addEventTypeAutoName(getClass().getPackage().getName());
        EPServiceProvider epService = EPServiceProviderManager.getDefaultProvider(config);
        runtime = epService.getEPRuntime();
        EPAdministrator administrator = epService.getEPAdministrator();

//        String expression = "select avg(price) from OrderEvent.win:time(30 sec)";
//        String expression = "select * from pattern [every b=NewBuildEvent() -> (timer:interval(3 seconds) and not NewBuildEvent(uuid = b.uuid))]";

//        String expression = "insert into BuildLastHour " +
//                "select job, " +
//                "sum(case result when 'SUCCESS' then 1 else 0 end) as successes, " +
//                "sum(case result when 'FAILURE' then 1 else 0 end) as failures " +
//                "from NewBuildEvent.win:time_batch(3 sec) group by job";

        String expression = "insert into BuildLastHour " +
                "select job, " +
                "sum(case result when 'SUCCESS' then 1 else 0 end) as successes, " +
                "sum(case result when 'FAILURE' then 1 else 0 end) as failures " +
                "from NewBuildEvent.win:length(10) " +
                "group by job " +
                "having " +
                "sum(case result when 'SUCCESS' then 1 else 0 end) > 1 AND " +
                "count(result) > 3";

        EPStatement statement = administrator.createEPL(expression);

        statement.addListener(new GenericListener(logger));

        UUID job1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID job2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

        send(job1, "SUCCESS"); sleep(100);
        send(job2, "SUCCESS"); sleep(100);
        send(job2, "FAILURE"); sleep(100);
        send(job2, "SUCCESS"); sleep(100);
        send(job2, "SUCCESS"); sleep(100);

        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            public void run() {
                logger.info("tick");
            }
        }, 0, 1000);

        while(true) {
            sleep(1000);
        }
    }

    public void send(UUID job, String result) {
        logger.info("Inserting " + result);
        runtime.sendEvent(new NewBuildEvent(job, UUID.randomUUID(), result));
    }

    class NewBuildEvent {
        private UUID job;
        private UUID uuid;
        private String result;

        NewBuildEvent(UUID job, UUID uuid, String result) {
            this.job = job;
            this.uuid = uuid;
            this.result = result;
        }

        public UUID getJob() {
            return job;
        }

        public UUID getUuid() {
            return uuid;
        }

        public String getResult() {
            return result;
        }
    }
}

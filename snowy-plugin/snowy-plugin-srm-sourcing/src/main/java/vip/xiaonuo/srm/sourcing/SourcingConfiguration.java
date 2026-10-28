package vip.xiaonuo.srm.sourcing;
import java.time.Clock;
import org.springframework.context.annotation.*;
@Configuration
public class SourcingConfiguration {@Bean public Clock sourcingClock(){return Clock.systemUTC();}}

package iwo.wintech.ngnfincalc.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.event.Level;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomStatusResolver {

    private final List<CustomStatus> customStatuses;

    public Optional<CustomStatus> resolve(Throwable ex) {
        return customStatuses.stream()
                .filter(cs -> cs.exceptionClass().isInstance(ex))
                .findFirst();
    }

    public int resolveStatusCode(Throwable ex, int defaultStatus) {
        return resolve(ex).map(CustomStatus::statusCode).orElse(defaultStatus);
    }

    public Level resolveLogLevel(Throwable ex, Level defaultLevel) {
        return resolve(ex).map(CustomStatus::logLevel).orElse(defaultLevel);
    }
}

package iwo.wintech.ngnfincalc.scenarios.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum PeriodDto {
    MONTHLY(12),
    QUARTERLY(4),
    BI_ANNUALLY(2),
    ANNUALLY(1),
    DEFAULT(12);

    private final int value;
}

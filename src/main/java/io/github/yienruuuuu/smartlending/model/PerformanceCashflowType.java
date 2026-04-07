package io.github.yienruuuuu.smartlending.model;

import java.math.BigDecimal;

public enum PerformanceCashflowType {
    DEPOSIT,
    WITHDRAWAL,
    INTERNAL_TRANSFER_IN,
    INTERNAL_TRANSFER_OUT;

    public BigDecimal toSignedAmount(BigDecimal absoluteAmount) {
        return switch (this) {
            case DEPOSIT, INTERNAL_TRANSFER_IN -> absoluteAmount;
            case WITHDRAWAL, INTERNAL_TRANSFER_OUT -> absoluteAmount.negate();
        };
    }

    public boolean isInternalTransfer() {
        return this == INTERNAL_TRANSFER_IN || this == INTERNAL_TRANSFER_OUT;
    }
}

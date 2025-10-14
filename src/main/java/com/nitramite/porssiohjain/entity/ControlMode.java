package com.nitramite.porssiohjain.entity;

public enum ControlMode {
    BELOW_MAX_PRICE, // turns ON when price < maxPriceSnt
    ABOVE_MAX_PRICE, // turns OFF when price > maxPriceSnt (inverse)
    MANUAL,          // manual override, use manualOn field
    SCHEDULED        // user defined schedule
}
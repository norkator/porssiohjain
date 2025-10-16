package com.nitramite.porssiohjain.entity;

public enum ControlMode {
    BELOW_MAX_PRICE, // turns ON when price < maxPriceSnt
    CHEAPEST_HOURS,  // daily cheapest hours, control on based on dailyOnMinutes
    MANUAL,          // manual override, use manualOn field
    SCHEDULED        // user defined schedule
}
package com.nitramite.porssiohjain.services.watermeters;

/** Safe translation keys only, without provider payloads or login credentials. */
public class WaterMeterException extends RuntimeException {
    public WaterMeterException(String key) {
        super(key);
    }
}

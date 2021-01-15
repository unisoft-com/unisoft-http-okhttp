package com.unisoft.http.okhttp;

import com.unisoft.core.util.UnisoftConfiguration;

/**
 * @author omar.H.Ajmi
 * @since 21/10/2020
 */
public class Configuration extends UnisoftConfiguration {
    public static final String READ_TIMEOUT = "READ_TIMEOUT";
    public static final String WRITE_TIMEOUT = "WRITE_TIMEOUT";
    public static final String CONNECTION_TIMEOUT = "CONNECTION_TIMEOUT";

    protected final String[] DEFAULT_CONFIGURATIONS = {
            UnisoftConfiguration.MAX_RETRY_COUNT,
            READ_TIMEOUT,
            WRITE_TIMEOUT,
            CONNECTION_TIMEOUT
    };
}

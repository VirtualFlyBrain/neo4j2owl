package ebi.spot.neo4j2owl;

import org.apache.log4j.spi.ErrorCode;

public class N2OException extends Exception {
    N2OException(String message, Throwable cause) {
        super(message, cause);
    }
}

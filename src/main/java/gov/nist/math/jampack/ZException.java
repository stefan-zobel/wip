package gov.nist.math.jampack;

/**
 * This is the exception class for Jampack. Since most errors in matrix
 * algorithms are unrecoverable, the standard response is to pass an error
 * message up the line.
 * 
 * @version Pre-alpha, 1999-02-24
 * @author G. W. Stewart
 */
@SuppressWarnings("serial")
public final class ZException extends RuntimeException {
    public ZException(String s) {
        super(s);
    }
}

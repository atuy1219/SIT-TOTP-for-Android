package com.atuy.sittotp;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class TotpTest {
    private static final String SEED = "JBSWY3DPEHPK3PXP";

    @Test
    public void normalizesBase32AndOtpAuthUri() {
        assertEquals(SEED, Totp.normalizeSeed("jbsw-y3dp ehpk3pxp=="));
        assertEquals(
                SEED,
                Totp.normalizeSeed("otpauth://totp/SIT?secret=JBSWY3DPEHPK3PXP&algorithm=SHA256")
        );
    }

    @Test
    public void generatesExpectedSha256Codes() {
        assertEquals("023015", Totp.generate(SEED, 0));
        assertEquals("023015", Totp.generate(SEED, 29));
        assertEquals("344551", Totp.generate(SEED, 30));
        assertEquals("730792", Totp.generate(SEED, 60));
        assertEquals("488545", Totp.generate(SEED, 1_234_567_890L));
    }

    @Test
    public void reportsSecondsUntilRollover() {
        assertEquals(30, Totp.remainingSeconds(0));
        assertEquals(1, Totp.remainingSeconds(29));
        assertEquals(30, Totp.remainingSeconds(30));
    }
}

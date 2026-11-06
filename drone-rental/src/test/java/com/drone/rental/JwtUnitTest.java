package com.drone.rental;
import com.drone.rental.security.JwtUtil;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class JwtUnitTest {
 private JwtUtil jwt(long expiration){var j=new JwtUtil();ReflectionTestUtils.setField(j,"secret","unit-test-only-32-byte-key-never-runtime-12345");ReflectionTestUtils.setField(j,"expiration",expiration);return j;}
 @Test void tamperedAndExpiredTokensAreRejected(){var j=jwt(10000L);String t=j.generateToken(1L,"u",0,"hash");assertTrue(j.validateToken(t));assertFalse(j.validateToken(t+"x"));assertFalse(jwt(-10000L).validateToken(jwt(-10000L).generateToken(1L,"u",0,"hash")));assertEquals(JwtUtil.stamp("hash"),j.getClaimsFromToken(t).get("credentialStamp"));}
 @Test void passwordResetChangesCredentialStamp(){assertNotEquals(JwtUtil.stamp("old"),JwtUtil.stamp("new"));}
}

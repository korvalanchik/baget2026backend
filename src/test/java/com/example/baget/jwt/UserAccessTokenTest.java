package com.example.baget.jwt;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class UserAccessTokenTest {
 JwtTokenUtil util() throws Exception {var u=new JwtTokenUtil();var f=JwtTokenUtil.class.getDeclaredField("secret");f.setAccessible(true);f.set(u,Base64.getEncoder().encodeToString(new byte[64]));return u;}
 VersionedUserDetails user(boolean enabled,long revision){return new VersionedUserDetails("manager","encoded",enabled,revision,List.of());}
 @Test void changedPermissionsInvalidateExistingToken() throws Exception {var u=util();var token=u.generateToken(user(true,0));assertTrue(u.validateToken(token,user(true,0)));assertFalse(u.validateToken(token,user(true,1)));}
 @Test void disabledUserCannotUseOrReceiveToken() throws Exception {var u=util();var token=u.generateToken(user(true,0));assertFalse(u.validateToken(token,user(false,0)));assertThrows(org.springframework.security.authentication.DisabledException.class,()->u.generateToken(user(false,0)));}
 @Test void oldTokenWorksOnlyUntilFirstRevision() throws Exception {var u=util();var token=u.generateToken(new org.springframework.security.core.userdetails.User("manager","encoded",List.of()));assertTrue(u.validateToken(token,user(true,0)));assertFalse(u.validateToken(token,user(true,1)));}
}

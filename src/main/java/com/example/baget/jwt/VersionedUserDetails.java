package com.example.baget.jwt;
import org.springframework.security.core.GrantedAuthority;
import java.util.Collection;
public class VersionedUserDetails extends org.springframework.security.core.userdetails.User {
 private final long accessRevision;
 public VersionedUserDetails(String username,String password,boolean enabled,long revision,Collection<? extends GrantedAuthority> authorities){super(username,password,enabled,true,true,true,authorities);this.accessRevision=revision;}
 public long getAccessRevision(){return accessRevision;}
}

package co.texerp.integrations.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
 private final JwtService jwtService;
 private final UserDetailsService userDetailsService;

 public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService uds) {
  this.jwtService = jwtService;
  this.userDetailsService = uds;
 }

 protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
  String h = req.getHeader("Authorization");
  if (h != null && h.startsWith("Bearer ")) {
   String token = h.substring(7);
   try {
    String username = jwtService.username(token);
    if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
     UserDetails ud = userDetailsService.loadUserByUsername(username);
     if (jwtService.validAccessToken(token, ud)) {
      var auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
      auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
      SecurityContextHolder.getContext().setAuthentication(auth);
     }
    }
   } catch (Exception ignored) {
   }
  }
  chain.doFilter(req, res);
 }
}

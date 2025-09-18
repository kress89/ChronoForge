package io.chronoforge.api;

import io.chronoforge.core.Determinism;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(5)
public class DeterminismFilter implements Filter {
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        var r = (HttpServletRequest) req;
        String seedHdr = r.getHeader("X-CF-Seed");
        String nodeHdr = r.getHeader("X-CF-Node");
        if (seedHdr != null && !seedHdr.isBlank()) {
            long seed = Long.parseLong(seedHdr.trim());
            Determinism.withDeterminism(nodeHdr, seed, () -> {
                try { chain.doFilter(req, res); } catch (IOException | ServletException e) { throw new RuntimeException(e); }
                return null;
            });
        } else {
            chain.doFilter(req, res);
        }
    }
}

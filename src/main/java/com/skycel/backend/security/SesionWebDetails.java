package com.skycel.backend.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** Detalles de la petición autenticada, más la sesión (idsesion) con la que inició el usuario. */
public class SesionWebDetails extends WebAuthenticationDetails {

    private final Integer idsesion;

    public SesionWebDetails(HttpServletRequest request, Integer idsesion) {
        super(request);
        this.idsesion = idsesion;
    }

    public Integer getIdsesion() {
        return idsesion;
    }
}

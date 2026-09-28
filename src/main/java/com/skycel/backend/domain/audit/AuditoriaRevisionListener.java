package com.skycel.backend.domain.audit;

import com.skycel.backend.domain.entity.CustomRevisionEntity;
import org.hibernate.envers.RevisionListener;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Al crear cada revisión, anota el usuario autenticado que hizo el cambio (o "sistema" si no hay uno). */
public class AuditoriaRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        CustomRevisionEntity revision = (CustomRevisionEntity) revisionEntity;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                ? auth.getName() : "sistema";
        revision.setUsername(username);
    }
}

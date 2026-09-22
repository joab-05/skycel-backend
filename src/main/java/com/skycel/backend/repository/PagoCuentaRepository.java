// ── PagoCuentaRepository.java ─────────────────────────────────────────────────
package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.PagoCuenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PagoCuentaRepository extends JpaRepository<PagoCuenta, Integer> {
    List<PagoCuenta> findByCuentaPorCobrarIdcuentaOrderByFechaPagoDesc(Integer idcuenta);

    Optional<PagoCuenta> findByClaveOffline(String claveOffline);
}
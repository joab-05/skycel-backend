package com.skycel.backend.repository;

import com.skycel.backend.domain.entity.ConfigComision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigComisionRepository extends JpaRepository<ConfigComision, Integer> {
}

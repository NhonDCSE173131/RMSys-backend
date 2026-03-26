package com.rmsys.backend.domain.repository;

import com.rmsys.backend.domain.entity.ToolCatalogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ToolCatalogRepository extends JpaRepository<ToolCatalogEntity, UUID> {
    List<ToolCatalogEntity> findByMachineId(UUID machineId);
}


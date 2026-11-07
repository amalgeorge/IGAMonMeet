package com.igamonmeet.template;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateVersionRepository extends JpaRepository<TemplateVersion, Long> {

    Optional<TemplateVersion> findFirstByOrderByVersionNoDesc();

    Optional<TemplateVersion> findByVersionNo(int versionNo);

    List<TemplateVersion> findAllByOrderByVersionNoDesc();
}

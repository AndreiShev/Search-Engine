package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.model.Site;

import java.util.Optional;

@Repository
public interface SiteRepository extends JpaRepository<Site, Integer> {
    @Modifying
    @Transactional
    Integer deleteByName(String name);

    Site findByName(String name);

    Site findByUrl(String url);

    Optional<Site> findById(Integer id);

}

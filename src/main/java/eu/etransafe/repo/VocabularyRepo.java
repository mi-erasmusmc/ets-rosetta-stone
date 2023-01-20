package eu.etransafe.repo;

import eu.etransafe.domain.Vocabulary;
import eu.etransafe.domain.Vocabulary.Identifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public interface VocabularyRepo extends JpaRepository<Vocabulary, Integer> {

    @Query(value = "SELECT v FROM Vocabulary v WHERE v.id IN :ids")
    List<Vocabulary> findByIds(Set<Identifier> ids);

}

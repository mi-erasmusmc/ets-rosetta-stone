package eu.etransafe.repo;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.ConceptRelationship;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface ConceptRelationshipRepo extends JpaRepository<ConceptRelationship, Integer> {

    @Query(value = """
            SELECT DISTINCT cr
            FROM ConceptRelationship cr
            WHERE cr.conceptOne IN :concepts
            AND cr.relationshipId IN :relationships
            AND cr.conceptTwo.domain IN :domains
            AND cr.conceptTwo.vocabulary IN :vocabularies
            """)
    List<ConceptRelationship> findMappings(Collection<Concept> concepts, Set<ConceptRelationship.Identifier> relationships,
                                           Set<Domain> domains, Set<Vocabulary.Identifier> vocabularies);

    @Query(value = """
            SELECT DISTINCT cr
            FROM ConceptRelationship cr
            WHERE cr.conceptTwo.vocabulary IN :vocabularies
            """)
    List<ConceptRelationship> findAllMappingsTo(Set<Vocabulary.Identifier> vocabularies);

    @Query(value = """
            SELECT DISTINCT cr
            FROM ConceptRelationship cr
            WHERE cr.conceptOne.vocabulary IN :from
            AND cr.conceptTwo.vocabulary IN :to
            AND cr.relationshipId = :mapsTo
            """)
    List<ConceptRelationship> findAllMappingsFromTo(Set<Vocabulary.Identifier> from, Set<Vocabulary.Identifier> to, ConceptRelationship.Identifier mapsTo);

    @Query(value = """
            SELECT cr
            FROM ConceptRelationship cr
            WHERE cr.conceptOne in :concept
            AND cr.conceptTwo.vocabulary IN :vocabularies
            """)
    List<ConceptRelationship> findMappings(Collection<Concept> concept, Set<Vocabulary.Identifier> vocabularies);


    @Query(value = """
            SELECT DISTINCT cr.conceptTwo
            FROM   ConceptRelationship cr
            WHERE cr.conceptOne = :child
            AND cr.relationshipId = :primary
            """)
    List<Concept> primaryParent(Concept child, ConceptRelationship.Identifier primary);

    @Query(value = """
            SELECT DISTINCT cr2.conceptOne.id
            FROM ConceptRelationship cr1
            JOIN ConceptRelationship cr2
            ON cr1.conceptTwo = cr2.conceptTwo
            WHERE cr1.conceptOne.id = :conceptId
            AND cr1.relationshipId IN :relations
            AND cr2.relationshipId IN :relations
            """)
    Set<Integer> findPotentialCombinationPartners(Integer conceptId, Set<ConceptRelationship.Identifier> relations);
}

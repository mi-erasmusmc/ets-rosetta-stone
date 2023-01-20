package eu.etransafe.repo;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.ConceptRelationship;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Vocabulary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Repository
public interface ConceptRepo extends JpaRepository<Concept, Integer> {

    List<Concept> findDistinctByConceptClass(String conceptClassId);

    List<Concept> findDistinctByCodeAndVocabularyIn(String conceptCode, Set<Vocabulary.Identifier> vocabularyIds);

    List<Concept> findDistinctByCodeInAndVocabularyIn(List<String> codes, Set<Vocabulary.Identifier> vocabularies);

    Set<Concept> findDistinctByNameAndVocabularyIn(String conceptName, Set<Vocabulary.Identifier> vocabularyIds);

    @Query(value = """
            SELECT DISTINCT c.*
            FROM concept_ancestor a, concept c
            WHERE a.ancestor_concept_id = :id
            AND c.concept_id = a.descendant_concept_id
            AND a.descendant_concept_id != a.ancestor_concept_id
            AND min_levels_of_separation = 1
            """, nativeQuery = true)
    List<Concept> findChildrenById(Integer id);

    @Query(value = """
            SELECT DISTINCT c.*
            FROM concept_relationship cr, concept c
            WHERE cr.concept_id_1 = :id
            AND c.concept_id = cr.concept_id_2
            AND cr.relationship_id = 'Is a'
            UNION
            SELECT DISTINCT c.*
            FROM concept_ancestor a, concept c
            WHERE a.descendant_concept_id = :id
            AND c.concept_id = a.ancestor_concept_id
            AND a.descendant_concept_id != a.ancestor_concept_id
            AND min_levels_of_separation = 1
            """, nativeQuery = true)
    List<Concept> findParentsById(Integer id);


    @Query(value = """
            SELECT DISTINCT c
            FROM ConceptRelationship cr, Concept c
            WHERE cr.conceptTwo.id = c.id
            AND cr.conceptOne.id = :conceptId
            AND cr.relationshipId IN :relationships
            AND c.domain IN :domains
            AND c.vocabulary IN :vocabularies
            """)
    Set<Concept> map(Integer conceptId, Set<ConceptRelationship.Identifier> relationships, Set<Domain> domains, Set<Vocabulary.Identifier> vocabularies);

    @Query(value = """
            SELECT DISTINCT c
            FROM ConceptRelationship cr, Concept c
            WHERE cr.conceptTwo.id = c.id
            AND cr.conceptOne.id = :conceptId
            AND cr.relationshipId IN :relationships
            AND c.vocabulary IN :vocabularies
            """)
    Set<Concept> map(Integer conceptId, Set<ConceptRelationship.Identifier> relationships, Set<Vocabulary.Identifier> vocabularies);


    @Query(value = """
                      SELECT DISTINCT cr.conceptTwo
                      FROM ConceptRelationship cr
                      WHERE cr.conceptOne = :concept
                      AND cr.relationshipId in :relationships
            """)
    Set<Concept> findFindingSites(Concept concept, Set<ConceptRelationship.Identifier> relationships);

    @Query(value = """
            SELECT DISTINCT concept_name AS concept_name
            FROM concept c
            WHERE MATCH (concept_name) AGAINST (:conceptName IN BOOLEAN MODE)
            AND c.domain_id IN :domains
            AND c.vocabulary_id IN :vocabularies
            AND (:conceptClass IS NULL OR c.concept_class_id = :conceptClass)
            ORDER BY LENGTH(concept_name)
            LIMIT :limit
            OFFSET :offset
            """, nativeQuery = true)
    List<String> matchConceptNames(String conceptName, List<String> domains, List<String> vocabularies, Integer limit, Integer offset, String conceptClass);

    @Query(value = """
            SELECT DISTINCT s.concept_synonym_name AS concept_name
            FROM concept c, concept_synonym s
            WHERE MATCH (s.concept_synonym_name) AGAINST (:conceptName IN BOOLEAN MODE)
            AND c.concept_id = s.concept_id
            AND c.domain_id IN :domains
            AND c.vocabulary_id IN :vocabularies
            AND (:conceptClass IS NULL OR c.concept_class_id = :conceptClass)
            ORDER BY LENGTH(s.concept_synonym_name)
            LIMIT :limit
            OFFSET :offset
            """, nativeQuery = true)
    List<String> matchConceptSynonyms(String conceptName, List<String> domains, List<String> vocabularies, Integer limit, Integer offset, String conceptClass);

    @Query(value = """
            SELECT DISTINCT cr.conceptTwo
            FROM ConceptRelationship cr
            WHERE cr.conceptOne in :concepts
            AND cr.relationshipId in :relationships
            AND cr.conceptTwo.vocabulary IN :vocabularies
            """)
    Set<Concept> findEqualConcepts(Set<Concept> concepts, Set<Vocabulary.Identifier> vocabularies, Set<ConceptRelationship.Identifier> relationships);

    @Query(value = """
            SELECT DISTINCT c
            FROM Concept c
            WHERE (:vocabulary is null or c.vocabulary = :vocabulary)
            AND (:domain is null or c.domain = :domain)
            AND (c.invalidReason is null or c.invalidReason NOT IN ('U', 'D'))
            """)
    List<Concept> findAllByVocabularyAndDomain(Vocabulary.Identifier vocabulary, Domain domain);

    @Query(value = """
            SELECT DISTINCT c
            FROM Concept c
            JOIN ConceptRelationship cr on cr.conceptOne = c
            WHERE (:vocabulary is null or c.vocabulary = :vocabulary)
            AND (:domain is null or c.domain = :domain)
            AND (c.invalidReason is null or c.invalidReason NOT IN ('U', 'D'))
            AND (cr.source = 'eTRANSAFE')
            """)
    List<Concept> findAllByVocabularyAndDomainMappedForETransafe(Vocabulary.Identifier vocabulary, Domain domain);

    @Query(value = """
                      SELECT DISTINCT cr.conceptOne
                      FROM ConceptRelationship cr
                      WHERE cr.conceptTwo = :site
                      AND cr.relationshipId in :relationships
            """)
    List<Concept> findingSitesOf(Concept site, EnumSet<ConceptRelationship.Identifier> relationships);

    List<Concept> findAllByNameInAndVocabularyInAndConceptClassIn(Collection<String> names, Set<Vocabulary.Identifier> vocabularies, List<String> conceptClasses);

    Set<Concept> findDistinctByName(String name);

    @Query(value = """
            SELECT DISTINCT c.*
            FROM concept_synonym
            JOIN concept c ON concept_synonym.concept_id = c.concept_id
            WHERE concept_synonym_name = :name
            """, nativeQuery = true)
    Set<Concept> findDistinctBySynonymName(String name);
}

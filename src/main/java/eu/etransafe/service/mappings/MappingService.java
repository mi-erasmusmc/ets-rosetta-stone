package eu.etransafe.service.mappings;

import eu.etransafe.domain.Concept;
import eu.etransafe.domain.ConceptRelationship;
import eu.etransafe.domain.Domain;
import eu.etransafe.domain.Mapping;
import eu.etransafe.domain.MappingItem;
import eu.etransafe.domain.Vocabulary;
import eu.etransafe.exception.RosettaException;
import eu.etransafe.repo.ConceptRelationshipRepo;
import eu.etransafe.repo.ConceptRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.etransafe.domain.CDMEnum.valueOfFromDb;
import static eu.etransafe.domain.ConceptRelationship.Identifier.*;
import static eu.etransafe.domain.Domain.CONDITION;
import static eu.etransafe.domain.Domain.MEASUREMENT;
import static eu.etransafe.domain.Domain.OBSERVATION;
import static eu.etransafe.domain.Domain.PROCEDURE;
import static eu.etransafe.domain.Vocabularies.INTERMEDIARY;
import static eu.etransafe.domain.Vocabulary.Identifier.MEDDRA;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.springframework.util.CollectionUtils.isEmpty;

@Slf4j
@Service
public class MappingService {

    public static final String CONCEPT_ID = "concept_id";
    public static final String CONCEPT_CODE = "concept_code";
    public static final String CONCEPT_NAME = "concept_name";
    public static final String VOCABULARY_ID = "vocabulary_id";
    public static final String DOMAIN_ID = "domain_id";
    public static final String CONCEPT_CLASS_ID = "concept_class_id";
    public static final Set<Concept> BODY_STRUCTURES = Set.of(new Concept().id(40481827), new Concept().id(4034052), new Concept().id(4237366), new Concept().id(70002975), new Concept().id(70000004));


    private final ConceptRelationshipRepo relationshipRepo;
    private final DataSource dataSource;
    private final MappingCache mappingCache;
    private final ConceptRepo conceptRepo;

    public MappingService(ConceptRelationshipRepo relationshipRepo, DataSource dataSource, MappingCache mappingCache, ConceptRepo conceptRepo) {
        this.relationshipRepo = relationshipRepo;
        this.dataSource = dataSource;
        this.mappingCache = mappingCache;
        this.conceptRepo = conceptRepo;
    }

    public Set<Concept> map(Concept concept, Set<ConceptRelationship.Identifier> relations, Set<Vocabulary.Identifier> vocabularies, Set<Domain> domains) {
        var rel = relations == null ? EnumSet.allOf(ConceptRelationship.Identifier.class) : relations;
        if (concept != null) {
            if (domains != null) {
                return conceptRepo.map(concept.id(), rel, domains, vocabularies);
            } else {
                return conceptRepo.map(concept.id(), rel, vocabularies);
            }
        }
        return emptySet();
    }


    private List<ConceptRelationship> getConceptRelationships(Set<Concept> concepts, Set<ConceptRelationship.Identifier> relations, Set<Vocabulary.Identifier> vocabularies, Set<Domain> domains) {
        return relationshipRepo.findMappings(concepts, relations, domains, vocabularies);
    }

    public Map<Concept, Set<Concept>> mapMultipleInSingleQuery(Set<Concept> concepts, Set<ConceptRelationship.Identifier> relations, Set<Vocabulary.Identifier> vocabularies, Set<Domain> domains) {
        if (!isEmpty(concepts)) {
            Map<Concept, Set<Concept>> result = new HashMap<>();
            getConceptRelationships(concepts, relations, vocabularies, domains).stream()
                    .filter(Objects::nonNull)
                    .collect(groupingBy(ConceptRelationship::conceptOne))
                    .forEach((concept, mapping) -> result.put(concept, concepts(mapping)));
            return result;
        }
        return Collections.emptyMap();
    }

    public Set<Concept> map(Set<Concept> concepts, Set<ConceptRelationship.Identifier> relations,
                            Set<Vocabulary.Identifier> vocabularies, Set<Domain> domains) {
        List<Set<Concept>> hits = new ArrayList<>();
        for (Concept concept : concepts) {
            var mapped = map(concept, relations, vocabularies, domains);
            hits.add(mapped);
        }
        return intersection(hits);
    }

    private Set<Concept> intersection(List<Set<Concept>> hits) {
        if (hits == null || hits.isEmpty()) {
            return emptySet();
        }
        var shared = hits.stream().flatMap(Collection::stream).collect(toSet());
        for (Set<Concept> set : hits) {
            shared.retainAll(set);
        }
        return shared;
    }

    private Set<Concept> concepts(List<ConceptRelationship> mapping) {
        return mapping.stream()
                .map(ConceptRelationship::conceptTwo)
                .collect(toSet());
    }

    public Set<Concept> mapSmqToPts(Concept smq) {
        Set<Concept> result = new HashSet<>();
        Map<Concept, Set<Concept>> meddra = mapMultipleInSingleQuery(Set.of(smq), EnumSet.of(SMQ_MEDDRA), EnumSet.of(MEDDRA), EnumSet.of(OBSERVATION, CONDITION, PROCEDURE, MEASUREMENT));
        meddra.forEach((k, v) -> result.addAll(v.stream().filter(m -> m.conceptClass().equalsIgnoreCase("PT")).toList()));
        log.info("Found {} MedDRA codes for SMQ {}", result.size(), smq.name());
        return result;
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    private Concept createConcept(ResultSet resultSet) throws SQLException {
        return new Concept()
                .id(resultSet.getInt(CONCEPT_ID))
                .code(resultSet.getString(CONCEPT_CODE))
                .name(resultSet.getString(CONCEPT_NAME))
                .vocabulary(valueOfFromDb(resultSet.getString(VOCABULARY_ID), Vocabulary.Identifier.class))
                .domain(valueOfFromDb(resultSet.getString(DOMAIN_ID), Domain.class))
                .conceptClass(resultSet.getString(CONCEPT_CLASS_ID));
    }


    // Dirty stuff for performance improvement in bulk mapping, should move to a repository class
    public Map<Integer, List<Concept>> getSocsForMEDDRAs(List<Integer> conceptIds, boolean primary) {
        if (isEmpty(conceptIds)) {
            return Collections.emptyMap();
        }
        String sql;
        if (primary) {
            sql = """
                    SELECT DISTINCT cr.concept_id_1 AS id, c.* FROM concept_relationship cr
                    JOIN concept c ON cr.concept_id_2 = c.concept_id
                    WHERE cr.relationship_id = 'PT - Primary SOC'
                    AND cr.concept_id_1 IN (?)
                    """;
        } else {
            sql = """
                    SELECT DISTINCT ca.descendant_concept_id AS id, c.*
                    FROM concept_ancestor ca
                    JOIN concept c ON ca.ancestor_concept_id = c.concept_id
                    WHERE ca.descendant_concept_id IN (?)
                    AND c.concept_class_id = 'SOC'
                    """;
        }
        Map<Integer, List<Concept>> sourceTargetMap = new HashMap<>(conceptIds.size());

        String questionMarks = conceptIds.stream().map(c -> "?").collect(Collectors.joining(","));
        sql = sql.replace("?", questionMarks);

        try (Connection conn = getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            int pos = 1;
            for (Integer id : conceptIds) {
                stmt.setInt(pos++, id);
            }
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                Integer source = rs.getInt("id");
                Concept concept = createConcept(rs);
                log.debug("{}    {}", source, concept.name());
                List<Concept> concepts = sourceTargetMap.getOrDefault(source, new ArrayList<>());
                concepts.add(concept);
                sourceTargetMap.put(source, concepts);
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new RosettaException("Something went wrong with the db");
        }
        log.debug("Found SOCs for {} out of {} concepts", sourceTargetMap.size(), conceptIds.size());
        return sourceTargetMap;
    }

    public Set<Mapping> preclinicalToSnomed(Concept concept) {
        return mappingCache.preclinicalToSnomed(concept);
    }

    public Set<Mapping> squash(Concept source, Collection<Mapping> finalMappings) {
        return squash(List.of(new MappingItem(source)), finalMappings);
    }

    // Squash deep mappings to simple 'to - from' mappings grouped by score
    public Set<Mapping> squash(Collection<MappingItem> source, Collection<Mapping> finalMappings) {
        Set<Mapping> resp = new HashSet<>();
        finalMappings.stream()
                .collect(groupingBy(Mapping::totalPenalty))
                .forEach((totalPenalty, maps) -> {
                    if (maps != null) {
                        var m = maps.stream().map(Mapping::to)
                                .filter(Objects::nonNull)
                                .flatMap(Collection::stream)
                                .collect(toSet());
                        var c = new Mapping()
                                .from(source)
                                .to(m)
                                .penalty(totalPenalty)
                                .description(MAPS_TO.value());
                        resp.add(c);
                    }
                });
        return resp;
    }

    public Set<Mapping> splitSnomed(Mapping mapping, Set<Vocabulary.Identifier> target) {
        Set<Mapping> mappings = new HashSet<>();
        mappings.add(mapping);
        mapping.to().forEach(mi -> {
            var req = map(mi.concepts(), EnumSet.of(HAS_FINDING_SITE, HAS_DIR_PROC_SITE, HAS_ASSO_MORPH), INTERMEDIARY, null);
            Set<Concept> concepts;
            if (req.size() > 2) {
                concepts = req.stream().filter(c -> !c.invalidReason().equals("U") && !c.invalidReason().equals("D")).collect(toSet());
            } else {
                concepts = req;
            }
            var m = new Mapping()
                    .precedingMapping(mapping)
                    .to(new MappingItem(concepts))
                    .description("SPLIT SNOMED");
            mappings.add(m);

            var optional = map(mi.concepts(), EnumSet.of(HAS_OCCURRENCE, HAS_CAUSATIVE_AGENT, HAS_COMPONENT,
                    HAS_DISPOSITION, HAS_PATHOLOGY, HAS_INTERPRETS), INTERMEDIARY, null)
                    .stream()
                    .filter(c -> mappingCache.isMappedToPreclinical(c, target))
                    .toList();
            if (!isEmpty(optional)) {
                Set<Concept> temp = new HashSet<>();
                temp.addAll(concepts);
                temp.addAll(optional);
                var extraM = new Mapping()
                        .precedingMapping(mapping)
                        .to(new MappingItem(temp))
                        .description("SPLIT SNOMED");
                mappings.add(extraM);
            }

        });
        return mappings;
    }

    public Mapping findOneBestMapping(Collection<Mapping> mappings) {
        if (mappings == null || mappings.isEmpty()) {
            throw new RosettaException("No mappings in list when looking for best mapping");
        }
        var bestScore = mappings.stream().mapToDouble(Mapping::totalPenalty).map(Math::abs).min().orElse(0);
        return mappings.stream().filter(cm -> Math.abs(cm.totalPenalty()) == bestScore).findAny().orElseThrow();
    }

    public Concept mapPrimaryParent(Concept concept) {
        var parents = relationshipRepo.primaryParent(concept);
        if (parents.size() == 1) {
            return parents.get(0);
        } else {
            log.warn("Something not quite right with primary parents of {}. Parents: {}", concept.string(), parents.stream().map(Concept::string).collect(Collectors.joining(", ")));
            return parents.stream().findAny().orElse(null);
        }
    }

    @Cacheable(value = "snomedOptions")
    public Set<Integer> findMappingOptionsSnomed(int id) {
        var options = relationshipRepo.findPotentialCombinationPartners(id,
                EnumSet.of(ASSO_MORPH_OF, FINDING_SITE_OF, DIR_PROC_SITE_OF, HAS_ASSO_MORPH, HAS_DIR_PROC_SITE,
                        HAS_FINDING_SITE, HAS_CAUSATIVE_AGENT, CAUSATIVE_AGENT_OF, PATHOLOGY_OF, OCCURRENCE_OF,
                        INTERPRETS_OF, COMPONENT_OF, DISPOSITION_OF));
        // Adding self to do a simple contains all further down
        options.add(id);
        return options;
    }

}

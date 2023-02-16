package eu.etransafe.domain;

import eu.etransafe.exception.RosettaException;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.springframework.util.CollectionUtils.isEmpty;

public enum Domain implements CDMEnum<Domain> {

    CONDITION("Condition"),
    CONDITION_STATUS("Condition Status"),
    COST("Cost"),
    DEVICE("Device"),
    EPISODE("Episode"),
    GENDER("Gender"),
    GEOGRAPHY("Geography"),
    MEAS_VALUE("Meas Value"),
    MEAS_VALUE_OPERATOR("Meas Value Operator"),
    MEASUREMENT("Measurement"),
    DRUG("Drug"),
    METADATA("Metadata"),
    OBSERVATION("Observation"),
    PAYER("Payer"),
    PLAN("Plan"),
    PLAN_STOP_REASON("Plan Stop Reason"),
    PROCEDURE("Procedure"),
    PROVIDER("Provider"),
    RELATIONSHIP("Relationship"),
    REVENUE_CODE("Revenue Code"),
    ROUTE("Route"),
    SPEC_ANATOMIC_SITE("Spec Anatomic Site"),
    SPEC_DISEASE_STATUS("Spec Disease Status"),
    SPECIMEN("Specimen"),
    SPONSOR("Sponsor"),
    TYPE_CONCEPT("Type Concept"),
    UNIT("Unit"),
    OTHER("Other"),
    VISIT("Visit");


    private final String value;

    Domain(String value) {
        this.value = value;
    }

    public static List<String> convert(Set<Domain> domains) {
        if (isEmpty(domains)) {
            return EnumSet.allOf(Domain.class).stream().map(Domain::value).toList();
        }
        return domains.stream().map(Domain::value).toList();
    }

    public static Set<Domain> convert(List<String> domains) {
        if (isEmpty(domains)) {
            return EnumSet.allOf(Domain.class);
        }
        return domains.stream().map(v -> CDMEnum.valueOfFromDb(v, Domain.class)).collect(Collectors.toSet());
    }

    public String value() {
        return this.value;
    }

    @Override
    public <E extends Enum<E> & CDMEnum<E>> E other(Class<E> type) {
        var other = OTHER;
        if (type.isInstance(other)) {
            return type.cast(other);
        } else {
            throw new RosettaException("This should not happen");
        }
    }
}

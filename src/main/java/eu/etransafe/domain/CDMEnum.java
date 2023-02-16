package eu.etransafe.domain;

import java.util.Arrays;

public interface CDMEnum<E extends Enum<E>> {


    static <E extends Enum<E> & CDMEnum<E>> E valueOfFromDb(String name, Class<E> type) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (E e : type.getEnumConstants()) {
            if (e.value().equalsIgnoreCase(name)) {
                return e;
            }
        }
        System.out.println("Parsing an unknown enum value from db: " + name);
        return Arrays.stream(type.getEnumConstants())
                .map(t -> t.other(type))
                .filter(o -> o.value().equalsIgnoreCase("other"))
                .findAny().orElseThrow(() -> new IllegalArgumentException("Unknown value " + name));
    }

    String value();

    <E extends Enum<E> & CDMEnum<E>> E other(Class<E> type);

}

package eu.etransafe.domain;

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
        throw new IllegalArgumentException("Unknown value " + name);
    }

    String value();

}

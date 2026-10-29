package project.api.domain.schedule.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

// Set<DayOfWeek> <-> "MO,WE,FR"
@Converter
public class DayOfWeekSetConverter implements AttributeConverter<Set<DayOfWeek>, String> {

    private static final String[] CODES = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};

    @Override
    public String convertToDatabaseColumn(Set<DayOfWeek> days) {
        if (days == null || days.isEmpty()) {
            return null;
        }
        return days.stream().sorted()
            .map(d -> CODES[d.getValue() - 1])
            .collect(Collectors.joining(","));
    }

    @Override
    public Set<DayOfWeek> convertToEntityAttribute(String db) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        if (db == null || db.isBlank()) {
            return days;
        }
        Arrays.stream(db.split(","))
            .map(String::trim)
            .forEach(code -> {
                for (int i = 0; i < CODES.length; i++) {
                    if (CODES[i].equals(code)) {
                        days.add(DayOfWeek.of(i + 1));
                    }
                }
            });
        return days;
    }
}

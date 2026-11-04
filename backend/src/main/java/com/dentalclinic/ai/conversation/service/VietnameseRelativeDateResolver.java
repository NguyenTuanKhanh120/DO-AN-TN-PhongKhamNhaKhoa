package com.dentalclinic.ai.conversation.service;

import com.dentalclinic.ai.conversation.model.ChatDateRange;
import com.dentalclinic.common.util.ClinicTimeProvider;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class VietnameseRelativeDateResolver {

    private static final Pattern ISO_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern DATE_WITH_YEAR = Pattern.compile(
            "^(?:ngay\\s+)?(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})$"
    );
    private static final Pattern DATE_WITHOUT_YEAR = Pattern.compile(
            "^(?:ngay\\s+)?(\\d{1,2})[/-](\\d{1,2})$"
    );
    private static final Pattern UPCOMING_DAYS = Pattern.compile(
            "^(?:trong\\s+)?(\\d{1,2})\\s+ngay\\s+(?:toi|sap toi)$"
    );
    private static final Map<String, DayOfWeek> WEEKDAYS = Map.ofEntries(
            Map.entry("thu hai", DayOfWeek.MONDAY),
            Map.entry("thu ba", DayOfWeek.TUESDAY),
            Map.entry("thu tu", DayOfWeek.WEDNESDAY),
            Map.entry("thu nam", DayOfWeek.THURSDAY),
            Map.entry("thu sau", DayOfWeek.FRIDAY),
            Map.entry("thu bay", DayOfWeek.SATURDAY),
            Map.entry("chu nhat", DayOfWeek.SUNDAY)
    );

    private final ClinicTimeProvider timeProvider;

    public VietnameseRelativeDateResolver(ClinicTimeProvider timeProvider) {
        this.timeProvider = timeProvider;
    }

    public Optional<ChatDateRange> resolve(String expression) {
        if (expression == null || expression.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalize(expression);
        LocalDate today = timeProvider.today();

        Optional<ChatDateRange> explicitDate = resolveExplicitDate(normalized, today);
        if (explicitDate.isPresent()) {
            return explicitDate;
        }
        return switch (normalized) {
            case "hom nay", "ngay hom nay" -> Optional.of(ChatDateRange.single(today));
            case "mai", "ngay mai" -> Optional.of(ChatDateRange.single(today.plusDays(1)));
            case "ngay kia" -> Optional.of(ChatDateRange.single(today.plusDays(2)));
            case "tuan nay" -> Optional.of(weekRange(today, 0));
            case "tuan sau" -> Optional.of(weekRange(today, 1));
            case "cuoi tuan nay" -> Optional.of(weekendRange(today, 0));
            case "cuoi tuan sau" -> Optional.of(weekendRange(today, 1));
            case "thang nay" -> Optional.of(monthRange(YearMonth.from(today)));
            case "thang sau" -> Optional.of(monthRange(YearMonth.from(today).plusMonths(1)));
            default -> resolveUpcomingDays(normalized, today)
                    .or(() -> resolveWeekday(normalized, today));
        };
    }

    private Optional<ChatDateRange> resolveExplicitDate(String value, LocalDate today) {
        try {
            if (ISO_DATE.matcher(value).matches()) {
                return Optional.of(ChatDateRange.single(LocalDate.parse(value)));
            }
            Matcher withYear = DATE_WITH_YEAR.matcher(value);
            if (withYear.matches()) {
                return Optional.of(ChatDateRange.single(LocalDate.of(
                        Integer.parseInt(withYear.group(3)),
                        Integer.parseInt(withYear.group(2)),
                        Integer.parseInt(withYear.group(1))
                )));
            }
            Matcher withoutYear = DATE_WITHOUT_YEAR.matcher(value);
            if (withoutYear.matches()) {
                return Optional.of(ChatDateRange.single(LocalDate.of(
                        today.getYear(),
                        Integer.parseInt(withoutYear.group(2)),
                        Integer.parseInt(withoutYear.group(1))
                )));
            }
        } catch (DateTimeException | NumberFormatException exception) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    private Optional<ChatDateRange> resolveUpcomingDays(String value, LocalDate today) {
        Matcher matcher = UPCOMING_DAYS.matcher(value);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        int dayCount = Integer.parseInt(matcher.group(1));
        if (dayCount < 1 || dayCount > 31) {
            return Optional.empty();
        }
        return Optional.of(new ChatDateRange(today, today.plusDays(dayCount - 1L)));
    }

    private Optional<ChatDateRange> resolveWeekday(String value, LocalDate today) {
        for (Map.Entry<String, DayOfWeek> entry : WEEKDAYS.entrySet()) {
            if (!value.contains(entry.getKey())) {
                continue;
            }
            LocalDate date;
            if (value.contains("tuan sau")) {
                date = startOfWeek(today).plusWeeks(1).with(entry.getValue());
            } else if (value.contains("tuan nay")) {
                date = startOfWeek(today).with(entry.getValue());
            } else {
                date = today.with(TemporalAdjusters.nextOrSame(entry.getValue()));
            }
            return Optional.of(ChatDateRange.single(date));
        }
        return Optional.empty();
    }

    private ChatDateRange weekRange(LocalDate today, int weeksToAdd) {
        LocalDate monday = startOfWeek(today).plusWeeks(weeksToAdd);
        return new ChatDateRange(monday, monday.plusDays(6));
    }

    private ChatDateRange weekendRange(LocalDate today, int weeksToAdd) {
        LocalDate saturday = startOfWeek(today).plusWeeks(weeksToAdd).plusDays(5);
        return new ChatDateRange(saturday, saturday.plusDays(1));
    }

    private ChatDateRange monthRange(YearMonth month) {
        return new ChatDateRange(month.atDay(1), month.atEndOfMonth());
    }

    private LocalDate startOfWeek(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private String normalize(String value) {
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9/-]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return decomposed.startsWith("vao ") ? decomposed.substring(4) : decomposed;
    }
}

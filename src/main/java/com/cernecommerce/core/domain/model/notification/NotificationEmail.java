package com.cernecommerce.core.domain.model.notification;

import java.util.ArrayList;
import java.util.List;

/**
 * E-mail operacional (caixa, operação, estoque, dev): título, um parágrafo e blocos de
 * "rótulo: valor". Um formato só para todos, renderizado pelo mesmo template — cada evento novo
 * vira uma montagem deste record, não um template e um método novos no {@code EmailPort}.
 *
 * @param category tag de métrica/log ({@code email.<category>}), ex.: {@code caixa.fechamento}
 * @param actionPath caminho no painel (ex.: {@code /app/pdv/caixas/12}); o adapter prefixa a URL do front
 */
public record NotificationEmail(
        String category,
        String subject,
        String title,
        String intro,
        Tone tone,
        List<Section> sections,
        String actionLabel,
        String actionPath) {

    public enum Tone { INFO, SUCCESS, WARNING, DANGER }

    public record Row(String label, String value, boolean highlight) {
        public static Row of(String label, Object value) {
            return new Row(label, value == null ? "—" : String.valueOf(value), false);
        }

        public static Row highlighted(String label, Object value) {
            return new Row(label, value == null ? "—" : String.valueOf(value), true);
        }
    }

    public record Section(String title, List<Row> rows) {
        public Section {
            rows = List.copyOf(rows);
        }
    }

    public NotificationEmail {
        sections = sections == null ? List.of() : List.copyOf(sections);
        tone = tone == null ? Tone.INFO : tone;
    }

    public static Builder builder(String category, String subject) {
        return new Builder(category, subject);
    }

    public static final class Builder {
        private final String category;
        private final String subject;
        private String title;
        private String intro;
        private Tone tone = Tone.INFO;
        private final List<Section> sections = new ArrayList<>();
        private String actionLabel;
        private String actionPath;

        private Builder(String category, String subject) {
            this.category = category;
            this.subject = subject;
            this.title = subject;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder intro(String intro) {
            this.intro = intro;
            return this;
        }

        public Builder tone(Tone tone) {
            this.tone = tone;
            return this;
        }

        public Builder section(String sectionTitle, List<Row> rows) {
            if (rows != null && !rows.isEmpty()) {
                sections.add(new Section(sectionTitle, rows));
            }
            return this;
        }

        public Builder action(String label, String path) {
            this.actionLabel = label;
            this.actionPath = path;
            return this;
        }

        public NotificationEmail build() {
            return new NotificationEmail(category, subject, title, intro, tone, sections, actionLabel, actionPath);
        }
    }
}

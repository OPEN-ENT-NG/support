package net.atos.entng.support.enums;

import java.util.Arrays;

public enum I18nKeys {
    ACTUALITES("actualites"),
    APIZIMBRA("apizimbra"),
    ARCHIVE("archive"),
    BLOG("blog"),
    CALENDAR("calendar"),
    COLLABORATIVEEDITOR("collaborativeeditor"),
    COLLABORATIVEWALL("collaborativewall"),
    COMMUNITY("community"),
    COMPETENCES("competences"),
    DIARY("diary"),
    EDT("edt"),
    EXERCIZER("exercizer"),
    FORUM("forum"),
    MINDMAP("mindmap"),
    OTHER("other"),
    POLL("poll"),
    RACK("rack"),
    RBS("rbs"),
    SCRAPBOOK("scrapbook"),
    SHAREBIGFILES("sharebigfiles"),
    SUPPORT("support"),
    TIMELINEGENERATOR("timelinegenerator"),
    WIKI("wiki"),
    WORKSPACEWORKSPACE("workspaceworkspace");

    private final String value;

    I18nKeys(String value) {
        this.value = value;
    }

    public String getValue() {
        return this.value;
    }

    public static I18nKeys getI18nKey(String value) {
        return Arrays.stream(I18nKeys.values())
                .filter(i18nKey -> i18nKey.getValue().equals(value))
                .findFirst()
                .orElse(null);
    }
}
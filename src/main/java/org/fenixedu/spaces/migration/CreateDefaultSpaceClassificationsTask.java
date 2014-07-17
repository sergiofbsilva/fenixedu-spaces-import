package org.fenixedu.spaces.migration;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.i18n.LocalizedString;
import org.fenixedu.spaces.domain.MetadataSpec;
import org.fenixedu.spaces.domain.SpaceClassification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;

@SuppressWarnings("unused")
public class CreateDefaultSpaceClassificationsTask extends CustomTask {

    private static final Logger logger = LoggerFactory.getLogger(CreateDefaultSpaceClassificationsTask.class);

    final Locale LocalePT = Locale.forLanguageTag("pt-PT");
    final Locale LocaleEN = Locale.forLanguageTag("en-GB");

    Multimap<String, MetadataSpec> codeToMetadataSpecMap;

    private void initMetadataSpecMap() {
        codeToMetadataSpecMap = HashMultimap.create();
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("observations", new LocalizedString.Builder().with(LocalePT, "Observações")
                        .with(LocaleEN, "Observations").build(), String.class, false, ""));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("description", new LocalizedString.Builder().with(LocalePT, "Descrição")
                        .with(LocaleEN, "Description").build(), java.lang.String.class, false, ""));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("ageQualitity", new LocalizedString.Builder().with(LocalePT, "Qualidade em idade")
                        .with(LocaleEN, "Age Quality").build(), java.lang.Boolean.class, true, "false"));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("distanceFromSanitaryInstalationsQuality", new LocalizedString.Builder()
                        .with(LocalePT, "Qualidade na distância às instalações sanitárias")
                        .with(LocaleEN, "Distance From Sanitary Instalations Quality").build(), java.lang.Boolean.class, true,
                        "false"));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("heightQuality", new LocalizedString.Builder().with(LocalePT, "Qualidade em altura")
                        .with(LocaleEN, "Height Quality").build(), java.lang.Boolean.class, true, "false"));
        codeToMetadataSpecMap.put("Room",
                new MetadataSpec("illuminationQuality", new LocalizedString.Builder().with(LocalePT, "Qualidade em iluminação")
                        .with(LocaleEN, "Illumination Quality").build(), java.lang.Boolean.class, true, "false"));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("securityQuality", new LocalizedString.Builder().with(LocalePT, "Qualidade em segurança")
                        .with(LocaleEN, "Security Quality").build(), java.lang.Boolean.class, true, "false"));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("doorNumber", new LocalizedString.Builder().with(LocalePT, "Número Porta")
                        .with(LocaleEN, "Door Number").build(), java.lang.String.class, false, ""));
        codeToMetadataSpecMap.put("Floor",
                new MetadataSpec("level", new LocalizedString.Builder().with(LocalePT, "Piso").with(LocaleEN, "Level").build(),
                        java.lang.Integer.class, true, "0"));
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("examCapacity", new LocalizedString.Builder().with(LocalePT, "Capacidade Exame")
                        .with(LocaleEN, "Exam Capacity").build(), java.lang.Integer.class, true, "0"));

    }

    private static class ClassificationBean {
        public String name;
        public Set<ClassificationBean> childs;
        public Integer code;

        public ClassificationBean(Integer code, String name, Set<ClassificationBean> childs) {
            super();
            this.code = code;
            this.name = name;
            this.childs = childs;
        }

        public ClassificationBean(Integer code, String name) {
            this(code, name, Sets.<ClassificationBean> newHashSet());
        }
    }

    private void createDefaultClassification() {
        ClassificationBean bean = new ClassificationBean(1, "Outros", new HashSet<>());
        create(null, bean);
    }

    final String[] en = new String[] { "Campus", "Room Subdivision", "Building", "Floor" };

    private void initAllClassificationsWithRoomMetadata() {
        taskLog("Init all classifications with room metadata");
        for (SpaceClassification classification : SpaceClassification.all()) {
            String content = classification.getName().getContent(LocaleEN);
            if (!Arrays.asList(en).contains(content)) {
                content = "Room";
            }
            if (!Strings.isNullOrEmpty(content)) {
                Collection<MetadataSpec> specs = codeToMetadataSpecMap.get(content);
                if (specs != null && !specs.isEmpty()) {
                    classification.setMetadataSpecs(specs);
                }
            }
        }
    }

    private void create(SpaceClassification parent, ClassificationBean bean) {
        final LocalizedString name = new LocalizedString.Builder().with(LocalePT, bean.name).build();
        final String code = bean.code.toString();
        final SpaceClassification spaceClassification = new SpaceClassification(code, name, parent);
        for (ClassificationBean child : bean.childs) {
            create(spaceClassification, child);
        }
    }

    public void initSpaceTypes() {
        taskLog("Init space types");
        final String[] pt = new String[] { "Campus", "Subdivisão de Sala", "Edifício", "Piso" };
        final String[] codes = new String[] { "1", "2", "3", "4" };

        final SpaceClassification otherSpaces = SpaceClassification.get("11"); // other spaces

        if (otherSpaces == null) {
            throw new UnsupportedOperationException("can't find other spaces");
        }

        for (int i = 0; i < codes.length; i++) {
            String name_EN = en[i];
            String name_PT = pt[i];
            String code = codes[i];
            create(otherSpaces, name_EN, name_PT, code);
        }
    }

    public void create(SpaceClassification parent, String name_EN, String name_PT, String code) {
        final LocalizedString name = new LocalizedString.Builder().with(LocalePT, name_PT).with(LocaleEN, name_EN).build();
        final SpaceClassification spaceClassification = new SpaceClassification(code, name, parent, null);
        spaceClassification.setMetadataSpecs(codeToMetadataSpecMap.get(code));
    }

    private void logAllImportedClassifications() {
        for (SpaceClassification classification : SpaceClassification.all()) {
            taskLog("code %s name %s\n", classification.getAbsoluteCode(), classification.getName().json().toString());
        }
    }

    public CreateDefaultSpaceClassificationsTask() {
        initMetadataSpecMap();
    };

    @Override
    public void runTask() throws Exception {
        if (Bennu.getInstance().getRootClassificationSet().isEmpty()) {
            taskLog("No classifications, create default classifications");
            createDefaultClassification();
            initSpaceTypes();
            initAllClassificationsWithRoomMetadata();
        } else {
            taskLog("classifications already imported");
        }
        logAllImportedClassifications();
    }
}
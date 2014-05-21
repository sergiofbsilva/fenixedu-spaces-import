package org.fenixedu.spaces.migration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import net.sourceforge.fenixedu.util.ConnectionManager;

import org.fenixedu.bennu.core.domain.Bennu;
import org.fenixedu.bennu.core.domain.groups.PersistentGroup;
import org.fenixedu.bennu.core.groups.Group;
import org.fenixedu.bennu.core.groups.NobodyGroup;
import org.fenixedu.bennu.scheduler.custom.CustomTask;
import org.fenixedu.commons.i18n.LocalizedString;
import org.fenixedu.spaces.domain.MetadataSpec;
import org.fenixedu.spaces.domain.Space;
import org.fenixedu.spaces.domain.SpaceClassification;
import org.fenixedu.spaces.domain.occupation.Occupation;
import org.fenixedu.spaces.domain.occupation.config.ExplicitConfig;
import org.fenixedu.spaces.domain.occupation.requests.OccupationRequest;
import org.fenixedu.spaces.ui.InformationBean;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.joda.time.YearMonthDay;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pt.ist.fenixframework.CallableWithoutException;
import pt.ist.fenixframework.FenixFramework;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.common.io.BaseEncoding;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;

@SuppressWarnings("unused")
public class ImportSpacesTask extends CustomTask {

    private static final Logger logger = LoggerFactory.getLogger(ImportSpacesTask.class);

    final Locale LocalePT = Locale.forLanguageTag("pt-PT");
    final Locale LocaleEN = Locale.forLanguageTag("en-GB");

    Multimap<String, MetadataSpec> codeToMetadataSpecMap;

    private static final String IMPORT_URL = "/home/sfbs/Documents/fenix-spaces/import/most_recent";
    private static final String EVENT_OCCUPATIONS_FILEPATH = IMPORT_URL + "/event_space_occupations.json";
    private static final String OCCUPATIONS_FILEPATH = IMPORT_URL + "/occupations.json";
    private static final String CLASSIFICATIONS_FILEPATH = IMPORT_URL + "/classifications.json";
    private static final String SPACES_FILEPATH = IMPORT_URL + "/spaces.json";

    private void initMetadataSpecMap() {
        codeToMetadataSpecMap = HashMultimap.create();
        codeToMetadataSpecMap.put(
                "Room",
                new MetadataSpec("observations", new LocalizedString.Builder().with(LocalePT, "Observações")
                        .with(LocaleEN, "Observations").build(), String.class, false, ""));
        codeToMetadataSpecMap.put("Room",
                new MetadataSpec("area", new LocalizedString.Builder().with(LocalePT, "Área").with(LocaleEN, "Area").build(),
                        BigDecimal.class, false, "0"));
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

    private void importClassifications(Gson gson) {
        taskLog("Import classification from %s \n", CLASSIFICATIONS_FILEPATH);
        try {
            File file = new File(CLASSIFICATIONS_FILEPATH);
            List<ClassificationBean> classificationJson;
            classificationJson = gson.fromJson(new JsonReader(new FileReader(file)), new TypeToken<List<ClassificationBean>>() {
            }.getType());
            for (ClassificationBean bean : classificationJson) {
                create(null, bean);
            }
        } catch (JsonIOException | JsonSyntaxException | FileNotFoundException e) {
            e.printStackTrace();
        }

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
        final String[] codes = new String[] { "3", "4", "5", "6" };

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

    private static String dealWithDates(YearMonthDay yearMonthDay) {
        return yearMonthDay == null ? null : yearMonthDay.toString("dd/MM/yyyy");
    }

    private static class IntervalBean {
        public String start;
        public String end;

        public IntervalBean(String start, String end) {
            super();
            this.start = start;
            this.end = end;
        }

    }

    private static class ImportEventSpaceOccupationBean {
        public String eventSpaceOccupation;
        public String space;

        public ImportEventSpaceOccupationBean(String eventSpaceOccupationExternalId, String space) {
            super();
            this.eventSpaceOccupation = eventSpaceOccupationExternalId;
            this.space = space;
        }
    }

    private static class ImportOccupationBean {

        public String description;
        public String title;
        public String frequency;
        public String beginDate;
        public String endDate;
        public String beginTime;
        public String endTime;
        public Boolean saturday;
        public Boolean sunday;
        public Set<IntervalBean> intervals;
        public Set<String> spaces;
        public String request;

        public ImportOccupationBean(String description, String title, String frequency, String beginDate, String endDate,
                String beginTime, String endTime, Boolean saturday, Boolean sunday, Set<String> spaces,
                Set<IntervalBean> intervals, String request) {
            super();
            this.description = description;
            this.title = title;
            this.frequency = frequency;
            this.beginDate = beginDate;
            this.endDate = endDate;
            this.beginTime = beginTime;
            this.endTime = endTime;
            this.saturday = saturday;
            this.sunday = sunday;
            this.spaces = spaces;
            this.intervals = intervals;
            this.request = request;
        }

        public List<Interval> getIntervals() {
            return FluentIterable.from(intervals).transform(new Function<IntervalBean, Interval>() {

                private final DateTimeFormatter dateTimeFormat = DateTimeFormat.forPattern("dd/MM/yyyy HH:mm:ss");

                @Override
                public Interval apply(IntervalBean input) {
                    final DateTime start = dateTimeFormat.parseDateTime(input.start);
                    final DateTime end = dateTimeFormat.parseDateTime(input.end);
                    return new Interval(start, end);
                }

            }).toList();
        }
    }

    public class SpaceBean {
        public String parentExternalId;
        public String externalId;
        public String createdOn;
        public Integer examCapacity;
        public Integer normalCapacity;
        public String type;
        public String occupationGroup;
        public String managementSpaceGroup;
        private String lessonOccupationsAccessGroup;
        private String writtenEvaluationOccupationsAccessGroup;
        public Set<SpaceInformationBean> informations;
        public Set<BlueprintBean> blueprints;

        public class BlueprintBean {
            public String validFrom;
            public String validUntil;
            public String creationPerson;
            public String raw;
        }

        public class SpaceInformationBean {
            public Integer capacity;
            public String blueprintNumber;
            public String validFrom;
            public String validUntil;
            public String emails;
            public Boolean ageQuality;
            public BigDecimal area;
            public String description;
            public Boolean distanceFromSanitaryInstalationsQuality;
            public String doorNumber;
            public Boolean heightQuality;
            public String identification;
            public Boolean illuminationQuality;
            public String observations;
            public Boolean securityQuality;
            public String classificationCode;
            public String name;
            public String level;
        }

        private DateTime dealWithDates(String datetime) {
            if (datetime == null) {
                return null;
            }
            return DateTimeFormat.forPattern("dd/MM/yyyy").parseDateTime(datetime);
        }

        public Set<InformationBean> beans() {
            return FluentIterable.from(informations).transform(new Function<SpaceInformationBean, InformationBean>() {

                Map<String, String> typeToCode;

                {
                    typeToCode = new HashMap<>();
                    typeToCode.put("Campus", "11.3");
                    typeToCode.put("RoomSubdivision", "11.4");
                    typeToCode.put("Building", "11.5");
                    typeToCode.put("Floor", "11.6");
                }

                @Override
                public InformationBean apply(SpaceInformationBean input) {
                    InformationBean bean = new InformationBean();
                    bean.setAllocatableCapacity(input.capacity);
                    bean.setBlueprintNumber(input.blueprintNumber);
                    final DateTime validFrom = dealWithDates(input.validFrom);
                    final DateTime validUntil = dealWithDates(input.validUntil);
                    bean.setValidFrom(validFrom);
                    bean.setValidUntil(validUntil);
                    bean.setArea(input.area);
                    bean.setIdentification(input.identification);
                    String classificationCode = input.classificationCode;
                    if (type.equals("Room")) {
                        if (Strings.isNullOrEmpty(classificationCode)) {
                            classificationCode = "3.6"; //Apoio ao Ensino - Outros
                        }
                        bean.setClassification(getClassificationByCode(removeLeadingZeros(classificationCode)));
                        bean.setMetadata(createMetadata(input));
                    } else {
                        bean.setClassification(getClassificationByType(type));
                    }
                    bean.setName(input.name);
                    bean.setRawBlueprint(getBlueprint(validFrom, validUntil));
                    return bean;
                }

                private byte[] getBlueprint(DateTime validFrom, DateTime validUntil) {
                    for (BlueprintBean bean : blueprints) {
                        final DateTime bFrom = dealWithDates(bean.validFrom);
                        final DateTime bUntil = dealWithDates(bean.validUntil);
                        if (new Interval(validFrom, validUntil).overlaps(new Interval(bFrom, bUntil))) {
                            return BaseEncoding.base64().decode(bean.raw);
                        }
                    }
                    return null;
                }

                private String removeLeadingZeros(String classificationCode) {
                    List<String> parts = new ArrayList<>();
                    for (String part : Splitter.on(".").split(classificationCode)) {
                        if (part.startsWith("0")) {
                            parts.add(part.substring(1));
                        } else {
                            parts.add(part);
                        }
                    }
                    return Joiner.on(".").join(parts);
                }

                private SpaceClassification getClassificationByType(String type) {
                    final String classificationCode = typeToCode.get(type);
                    return SpaceClassification.get(classificationCode);
                }

                private SpaceClassification getClassificationByCode(String classificationCode) {
                    final SpaceClassification spaceClassification = SpaceClassification.get(classificationCode);
                    if (spaceClassification == null) {
                        throw new RuntimeException("code doesnt exist: " + classificationCode);
                    }
                    return spaceClassification;
                }

                private Map<String, String> createMetadata(SpaceInformationBean bean) {
                    Map<String, String> metadata = new HashMap<>();
                    metadata.put("ageQualitity", dealWithBooleans(bean.ageQuality));
                    metadata.put("heightQuality", dealWithBooleans(bean.heightQuality));
                    metadata.put("illuminationQuality", dealWithBooleans(bean.illuminationQuality));
                    metadata.put("securityQuality", dealWithBooleans(bean.securityQuality));
                    metadata.put("distanceFromSanitaryInstalationsQuality",
                            dealWithBooleans(bean.distanceFromSanitaryInstalationsQuality));
                    metadata.put("doorNumber", bean.doorNumber);
                    metadata.put("description", bean.description);
                    if (bean.area != null) {
                        metadata.put("area", bean.area.toString());
                    }
                    if (bean.level != null) {
                        metadata.put("level", bean.level);
                    }
                    return metadata;
                }

                public String dealWithBooleans(Boolean bool) {
                    return bool == null ? "false" : bool.toString();
                }

            }).toSet();
        }
    }

//    Map<SpaceBean, Space> beanToSpaceMap = new HashMap<>();
//    Map<String, SpaceBean> idToBeansMap = new HashMap<>();
//    List<SpaceBean> fromJson;

    private void doClassifications(final Gson gson) {
        FenixFramework.getTransactionManager().withTransaction(new CallableWithoutException<Void>() {

            @Override
            public Void call() {
                if (Bennu.getInstance().getRootClassificationSet().isEmpty()) {
                    taskLog("No classifications, import classifications");
                    importClassifications(gson);
                    initSpaceTypes();
                    initAllClassificationsWithRoomMetadata();
                } else {
                    taskLog("classifications already imported");
                }
                return null;
            }

        });

        logAllImportedClassifications();
    }

    private void logAllImportedClassifications() {
        for (SpaceClassification classification : SpaceClassification.all()) {
            taskLog("code %s name %s\n", classification.getAbsoluteCode(), classification.getName().json().toString());
        }
    }

    @Override
    public void runTask() throws Exception {
        Gson gson = new Gson();
        initMetadataSpecMap();
        doClassifications(gson);
        processSpaces(gson);
//        processOccupations(gson);
    }

    public void processOccupations(Gson gson) throws FileNotFoundException {
        File file = new File(OCCUPATIONS_FILEPATH);
        final List<ImportOccupationBean> fromJson =
                gson.fromJson(new JsonReader(new FileReader(file)), new TypeToken<List<ImportOccupationBean>>() {
                }.getType());

        for (ImportOccupationBean importOccupationBean : fromJson) {
            Set<Space> occupationSpaces = new HashSet<>();
            for (String spaceId : importOccupationBean.spaces) {
                if (!Strings.isNullOrEmpty(spaceId)) {
                    final Space e = FenixFramework.getDomainObject(getNewSpaceId(spaceId));
                    if (!FenixFramework.isDomainObjectValid(e)) {
                        throw new UnknownError(String.format("Space doesn't exist %s, abort!", spaceId));
                    }
                    occupationSpaces.add(e);
                }
            }

            final ExplicitConfig explicitConfig = new ExplicitConfig(new JsonObject(), importOccupationBean.getIntervals());

            final Occupation occupation =
                    new Occupation(importOccupationBean.title, importOccupationBean.description, explicitConfig);

            for (Space space : occupationSpaces) {
                occupation.addSpace(space);
            }

            String requestId = importOccupationBean.request;
            if (!Strings.isNullOrEmpty(requestId)) {
                OccupationRequest request = FenixFramework.getDomainObject(requestId);

                if (FenixFramework.isDomainObjectValid(request)) {
                    occupation.setRequest(request);
                }
            }
        }
    }

    public void processSpaces(Gson gson) throws FileNotFoundException {
        File file = new File(SPACES_FILEPATH);
        final List<SpaceBean> fromJson = gson.fromJson(new JsonReader(new FileReader(file)), new TypeToken<List<SpaceBean>>() {
        }.getType());

//        for (SpaceBean spaceBean : fromJson) {
//            idToBeansMap.put(spaceBean.externalId, spaceBean);
//        }

        final List<List<SpaceBean>> partitions = Lists.partition(fromJson, 1000);
        taskLog("Processing chunks of 1000, total : %d\n", partitions.size());
        for (List<SpaceBean> partition : partitions) {
            taskLog("Chunk with %d \n", partition.size());
            processPartition(partition);
        }
    }

    private void processPartition(final List<SpaceBean> partition) {
        FenixFramework.getTransactionManager().withTransaction(new CallableWithoutException<Void>() {

            @Override
            public Void call() {
                for (final SpaceBean bean : partition) {
                    process(bean);

                }
                return null;
            }
        });
    }

    private Space process(final SpaceBean spaceBean) {
        if (spaceBean == null) {
            return null;
        }
        return update((Space) FenixFramework.getDomainObject(getNewSpaceId(spaceBean.externalId)), spaceBean);
//        if (!beanToSpaceMap.containsKey(spaceBean)) {
//            beanToSpaceMap.put(spaceBean, create(process(idToBeansMap.get(spaceBean.parentExternalId)), spaceBean));
//        }
//        return beanToSpaceMap.get(spaceBean);
    }

    private String getNewSpaceId(String externalId) {
        final long oid = Long.parseLong(externalId);
        final int idInternal = (int) (oid & 0x0000FFFF);
        final long cid = getSpaceCID() << 32;
        return Long.toString(cid + (idInternal >> 32));
    }

    private static Long spaceCid;

    private Long getSpaceCID() {
        if (spaceCid == null) {
            spaceCid = initCID();
        }
        return spaceCid;
    }

    private static Long initCID() {
        Connection connection = ConnectionManager.getCurrentSQLConnection();
        Statement statement = null;
        try {
            statement = connection.createStatement();
            String query =
                    String.format("select DOMAIN_CLASS_ID from FF$DOMAIN_CLASS_INFO where DOMAIN_CLASS_NAME = '%s'",
                            Space.class.getName());

            ResultSet rs = statement.executeQuery(query);
            while (rs.next()) {
                return rs.getLong("DOMAIN_CLASS_ID");
            }
            return null;
        } catch (SQLException e) {
            throw new Error(e);
        } finally {
            try {
                if (statement != null) {
                    statement.close();
                }
            } catch (SQLException e) {
                throw new Error(e);
            }
        }
    }

    private Space update(Space space, SpaceBean spaceBean) {
        for (InformationBean infoBean : spaceBean.beans()) {
            infoBean.getMetadata().put("examCapacity", spaceBean.examCapacity == null ? null : spaceBean.examCapacity.toString());
//            infoBean.getMetadata().put("normalCapacity",
//                    spaceBean.normalCapacity == null ? null : spaceBean.normalCapacity.toString());
            if (spaceBean.normalCapacity != null) {
                infoBean.setAllocatableCapacity(spaceBean.normalCapacity);
            }
            space.bean(infoBean);
        }
        final PersistentGroup occupationGroup = FenixFramework.getDomainObject(spaceBean.occupationGroup);
        final PersistentGroup lessonOccupationsAccessGroup =
                FenixFramework.getDomainObject(spaceBean.lessonOccupationsAccessGroup);
        final PersistentGroup writtenEvaluationOccupationsAccessGroup =
                FenixFramework.getDomainObject(spaceBean.writtenEvaluationOccupationsAccessGroup);
        final PersistentGroup managementGroup = FenixFramework.getDomainObject(spaceBean.managementSpaceGroup);

        Group group = NobodyGroup.get();

        if (occupationGroup != null) {
            group = group.or(occupationGroup.toGroup());
        }

        if (lessonOccupationsAccessGroup != null) {
            group = group.or(lessonOccupationsAccessGroup.toGroup());
        }

        if (writtenEvaluationOccupationsAccessGroup != null) {
            group = group.or(writtenEvaluationOccupationsAccessGroup.toGroup());
        }

        space.setOccupationsAccessGroup(group.equals(NobodyGroup.get()) ? null : group);

        if (FenixFramework.isDomainObjectValid(managementGroup)) {
            space.setManagementAccessGroup(managementGroup.toGroup());
        } else {
            space.setManagementAccessGroup((Group) null);
        }
        return space;
    }

//    private Space create(final Space parent, final SpaceBean spaceBean) {
//        return FenixFramework.getTransactionManager().withTransaction(new CallableWithoutException<Space>() {
//
//            @Override
//            public Space call() {
//                return innerCreate(parent, spaceBean);
//            }
//
//        });
//    }

//    private Space innerCreate(Space parent, SpaceBean spaceBean) {
//        Space space = new Space(parent, (Information) null);
//        for (InformationBean infoBean : spaceBean.beans()) {
//            infoBean.getMetadata().put("examCapacity", spaceBean.examCapacity == null ? null : spaceBean.examCapacity.toString());
//            if (spaceBean.normalCapacity != null) {
//                infoBean.setAllocatableCapacity(spaceBean.normalCapacity);
//            }
//            space.bean(infoBean);
//        }
//        final PersistentGroup occupationGroup = FenixFramework.getDomainObject(spaceBean.occupationGroup);
//        final PersistentGroup lessonOccupationsAccessGroup =
//                FenixFramework.getDomainObject(spaceBean.lessonOccupationsAccessGroup);
//        final PersistentGroup writtenEvaluationOccupationsAccessGroup =
//                FenixFramework.getDomainObject(spaceBean.writtenEvaluationOccupationsAccessGroup);
//        final PersistentGroup managementGroup = FenixFramework.getDomainObject(spaceBean.managementSpaceGroup);
//
//        Group group = NobodyGroup.get();
//
//        if (occupationGroup != null) {
//            group = group.or(occupationGroup.toGroup());
//        }
//
//        if (lessonOccupationsAccessGroup != null) {
//            group = group.or(lessonOccupationsAccessGroup.toGroup());
//        }
//
//        if (writtenEvaluationOccupationsAccessGroup != null) {
//            group = group.or(writtenEvaluationOccupationsAccessGroup.toGroup());
//        }
//
//        space.setOccupationsAccessGroup(group.equals(NobodyGroup.get()) ? null : group);
//
//        if (FenixFramework.isDomainObjectValid(managementGroup)) {
//            space.setManagementAccessGroup(managementGroup.toGroup());
//        } else {
//            space.setManagementAccessGroup((Group) null);
//        }
//
//        return space;
//    }

}
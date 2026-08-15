/*
 * Pörssiohjain - Energy usage optimization platform
 * Copyright (C) 2026  Martin Kankaanranta / Nitramite Tmi
 *
 * This source code is licensed under the Pörssiohjain Personal Use License v1.0.
 * Private self-hosting for personal household use is permitted.
 * Commercial use, resale, managed hosting, or offering the software as a
 * service to third parties requires separate written permission.
 * See LICENSE for details.
 */

package com.nitramite.porssiohjain.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nitramite.porssiohjain.entity.AccountEntity;
import com.nitramite.porssiohjain.entity.DeviceAcCommandLogEntity;
import com.nitramite.porssiohjain.entity.HeatingPlannerPlanPointEntity;
import com.nitramite.porssiohjain.entity.PowerLimitHistoryEntity;
import com.nitramite.porssiohjain.entity.ProductionHistoryEntity;
import com.nitramite.porssiohjain.entity.ZigbeeDeviceMeasurementEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PersistenceUnitUtil;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.IdentifiableType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class AccountDataExportService {

    private static final int ID_BATCH_SIZE = 500;
    private static final Set<Class<?>> HIGH_VOLUME_ENTITY_CLASSES = Set.of(
            DeviceAcCommandLogEntity.class,
            HeatingPlannerPlanPointEntity.class,
            PowerLimitHistoryEntity.class,
            ProductionHistoryEntity.class,
            ZigbeeDeviceMeasurementEntity.class
    );

    private final EntityManager entityManager;
    private final EntityManagerFactory entityManagerFactory;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Transactional(readOnly = true)
    public byte[] exportAccountData(Long accountId) {
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();
        List<EntityType<?>> entityTypes = entityManager.getMetamodel().getEntities().stream()
                .sorted(Comparator.comparing(EntityType::getName))
                .toList();
        Set<Class<?>> exportableEntityClasses = findExportableEntityClasses(entityTypes);

        Map<Class<?>, Set<Object>> ownedIds = new LinkedHashMap<>();
        for (EntityType<?> entityType : entityTypes) {
            if (!exportableEntityClasses.contains(entityType.getJavaType())) {
                continue;
            }
            if (isHighVolumeEntity(entityType.getJavaType())) {
                continue;
            }
            ownedIds.put(entityType.getJavaType(), new LinkedHashSet<>());
        }
        ownedIds.computeIfAbsent(AccountEntity.class, ignored -> new LinkedHashSet<>()).add(accountId);

        boolean changed;
        do {
            changed = false;
            for (EntityType<?> entityType : entityTypes) {
                Class<?> entityClass = entityType.getJavaType();
                if (!exportableEntityClasses.contains(entityClass)) {
                    continue;
                }
                if (isHighVolumeEntity(entityClass)) {
                    continue;
                }

                Set<Object> discoveredIds = findOwnedIds(entityType, accountId, ownedIds);
                if (ownedIds.get(entityClass).addAll(discoveredIds)) {
                    changed = true;
                }
            }
        } while (changed);

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("schemaVersion", 1);
        export.put("generatedAt", Instant.now());
        export.put("accountId", accountId);
        export.put("tables", buildTables(entityTypes, ownedIds, persistenceUnitUtil));
        export.put("omittedHighVolumeTables", buildHighVolumeTableSummaries(entityTypes, exportableEntityClasses, accountId));

        try {
            return buildZipExport(export);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize account data export", ex);
        }
    }

    private byte[] buildZipExport(Map<String, Object> export) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("README.txt"));
            zip.write("""
                    Pörssiohjain account data export

                    account-data.json contains account settings, devices, controls, planner settings, and other account-owned configuration data.
                    Some high-volume time-series data is summarized in omittedHighVolumeTables with row counts and time ranges instead of being included row by row.
                    This export is intended for reviewing the personal information and configuration data stored for your account.
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("account-data.json"));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(export));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private List<Map<String, Object>> buildTables(
            List<EntityType<?>> entityTypes,
            Map<Class<?>, Set<Object>> ownedIds,
            PersistenceUnitUtil persistenceUnitUtil
    ) {
        List<Map<String, Object>> tables = new ArrayList<>();
        for (EntityType<?> entityType : entityTypes) {
            Class<?> entityClass = entityType.getJavaType();
            Set<Object> ids = ownedIds.get(entityClass);
            if (ids == null || ids.isEmpty()) {
                continue;
            }

            List<Map<String, Object>> rows = findRowsByIds(entityType, ids).stream()
                    .sorted(Comparator.comparing(row -> String.valueOf(persistenceUnitUtil.getIdentifier(row))))
                    .map(row -> serializeEntity(row, entityClass, persistenceUnitUtil))
                    .toList();

            Map<String, Object> table = new LinkedHashMap<>();
            table.put("entity", entityClass.getSimpleName());
            table.put("rowCount", rows.size());
            table.put("rows", rows);
            tables.add(table);
        }
        return tables;
    }

    private List<Map<String, Object>> buildHighVolumeTableSummaries(
            List<EntityType<?>> entityTypes,
            Set<Class<?>> exportableEntityClasses,
            Long accountId
    ) {
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (EntityType<?> entityType : entityTypes) {
            Class<?> entityClass = entityType.getJavaType();
            if (!exportableEntityClasses.contains(entityClass) || !isHighVolumeEntity(entityClass)) {
                continue;
            }

            String accountPredicate = accountPredicate(entityClass);
            if (accountPredicate == null) {
                continue;
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("entity", entityClass.getSimpleName());
            summary.put("reason", "High-volume time-series data is omitted from account information export to avoid excessive memory and file size.");
            summary.put("rowCount", countRows(entityType, accountPredicate, accountId));

            String timeField = summaryTimeField(entityClass);
            if (timeField != null) {
                Object[] range = timeRange(entityType, accountPredicate, accountId, timeField);
                summary.put("earliest", range[0]);
                summary.put("latest", range[1]);
            }

            summaries.add(summary);
        }
        return summaries;
    }

    private long countRows(EntityType<?> entityType, String accountPredicate, Long accountId) {
        return entityManager.createQuery(
                        "select count(e) from " + entityType.getName() + " e where " + accountPredicate,
                        Long.class
                )
                .setParameter("accountId", accountId)
                .getSingleResult();
    }

    private Object[] timeRange(EntityType<?> entityType, String accountPredicate, Long accountId, String timeField) {
        Object[] result = entityManager.createQuery(
                        "select min(e." + timeField + "), max(e." + timeField + ") from "
                                + entityType.getName() + " e where " + accountPredicate,
                        Object[].class
                )
                .setParameter("accountId", accountId)
                .getSingleResult();
        return result != null ? result : new Object[]{null, null};
    }

    private Set<Object> findOwnedIds(
            EntityType<?> entityType,
            Long accountId,
            Map<Class<?>, Set<Object>> ownedIds
    ) {
        String idFieldName = getIdFieldName(entityType);
        if (idFieldName == null) {
            return Set.of();
        }

        Set<Object> ids = new LinkedHashSet<>();
        Class<?> entityClass = entityType.getJavaType();
        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (AccountEntity.class.isAssignableFrom(field.getType())) {
                ids.addAll(findIdsByQuery(entityType, idFieldName, "e." + field.getName() + ".id = :accountId",
                        Map.of("accountId", accountId)));
                continue;
            }

            if (isAccountIdField(field)) {
                ids.addAll(findIdsByQuery(entityType, idFieldName, "e." + field.getName() + " = :accountId",
                        Map.of("accountId", accountId)));
                continue;
            }

            if (isEntityReference(field)) {
                Set<Object> referenceIds = ownedIds.get(field.getType());
                if (referenceIds != null && !referenceIds.isEmpty()) {
                    ids.addAll(findIdsByInBatches(entityType, idFieldName, "e." + field.getName() + ".id", referenceIds));
                }
                continue;
            }

            Set<Object> foreignKeyIds = ownedForeignKeyIds(field, ownedIds);
            if (!foreignKeyIds.isEmpty()) {
                ids.addAll(findIdsByInBatches(entityType, idFieldName, "e." + field.getName(), foreignKeyIds));
            }
        }
        return ids;
    }

    private List<Object> findIdsByQuery(
            EntityType<?> entityType,
            String idFieldName,
            String whereClause,
            Map<String, Object> parameters
    ) {
        var query = entityManager.createQuery(
                "select distinct e." + idFieldName + " from " + entityType.getName() + " e where " + whereClause,
                Object.class
        );
        parameters.forEach(query::setParameter);
        return query.getResultList();
    }

    private List<Object> findIdsByInBatches(
            EntityType<?> entityType,
            String idFieldName,
            String expression,
            Set<Object> candidateIds
    ) {
        List<Object> ids = new ArrayList<>();
        List<Object> candidates = new ArrayList<>(candidateIds);
        for (int start = 0; start < candidates.size(); start += ID_BATCH_SIZE) {
            List<Object> batch = candidates.subList(start, Math.min(start + ID_BATCH_SIZE, candidates.size()));
            ids.addAll(findIdsByQuery(entityType, idFieldName, expression + " in :ids", Map.of("ids", batch)));
        }
        return ids;
    }

    private List<Object> findRowsByIds(EntityType<?> entityType, Set<Object> ids) {
        String idFieldName = getIdFieldName(entityType);
        if (idFieldName == null) {
            return List.of();
        }

        List<Object> rows = new ArrayList<>();
        List<Object> idList = new ArrayList<>(ids);
        for (int start = 0; start < idList.size(); start += ID_BATCH_SIZE) {
            List<Object> batch = idList.subList(start, Math.min(start + ID_BATCH_SIZE, idList.size()));
            rows.addAll(entityManager.createQuery(
                            "select e from " + entityType.getName() + " e where e." + idFieldName + " in :ids",
                            Object.class
                    )
                    .setParameter("ids", batch)
                    .getResultList());
        }
        return rows;
    }

    private String getIdFieldName(EntityType<?> entityType) {
        if (entityType instanceof IdentifiableType<?> identifiableType && identifiableType.hasSingleIdAttribute()) {
            Class<?> idType = identifiableType.getIdType() != null
                    ? identifiableType.getIdType().getJavaType()
                    : null;
            if (idType != null) {
                return identifiableType.getId(idType).getName();
            }
        }

        return getFields(entityType.getJavaType()).stream()
                .filter(field -> field.isAnnotationPresent(Id.class))
                .map(Field::getName)
                .findFirst()
                .orElse(null);
    }

    private Set<Class<?>> findExportableEntityClasses(List<EntityType<?>> entityTypes) {
        Set<Class<?>> entityClasses = new LinkedHashSet<>();
        entityTypes.forEach(entityType -> entityClasses.add(entityType.getJavaType()));

        Set<Class<?>> exportable = new LinkedHashSet<>();
        exportable.add(AccountEntity.class);

        boolean changed;
        do {
            changed = false;
            for (Class<?> entityClass : entityClasses) {
                if (exportable.contains(entityClass)) {
                    continue;
                }
                if (canBelongToExport(entityClass, exportable)) {
                    exportable.add(entityClass);
                    changed = true;
                }
            }
        } while (changed);

        return exportable;
    }

    private boolean canBelongToExport(Class<?> entityClass, Set<Class<?>> exportable) {
        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            if (AccountEntity.class.isAssignableFrom(field.getType()) || isAccountIdField(field)) {
                return true;
            }

            if (isEntityReference(field) && exportable.contains(field.getType())) {
                return true;
            }

            if (isForeignKeyToExportableEntity(field, exportable)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> serializeEntity(Object row, Class<?> entityClass, PersistenceUnitUtil persistenceUnitUtil) {
        Map<String, Object> output = new LinkedHashMap<>();
        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }

            Object value = readField(field, row);
            output.put(field.getName(), serializeValue(field, value, persistenceUnitUtil));
        }
        return output;
    }

    private Object serializeValue(Field field, Object value, PersistenceUnitUtil persistenceUnitUtil) {
        if (value instanceof Collection<?> || value instanceof Map<?, ?>) {
            return "collection exported through related entity rows";
        }

        if (value == null || isSimpleValue(value)) {
            return value;
        }

        if (isEntityReference(field)) {
            Map<String, Object> reference = new LinkedHashMap<>();
            reference.put("entity", field.getType().getSimpleName());
            reference.put("id", persistenceUnitUtil.getIdentifier(value));
            return reference;
        }

        return String.valueOf(value);
    }

    private Object readField(Field field, Object row) {
        try {
            field.setAccessible(true);
            return field.get(row);
        } catch (IllegalAccessException ex) {
            throw new IllegalStateException("Failed to read " + field.getName(), ex);
        }
    }

    private boolean isForeignKeyToExportableEntity(Field field, Set<Class<?>> exportable) {
        if (!Long.class.equals(field.getType()) || !field.getName().endsWith("Id")) {
            return false;
        }

        String prefix = field.getName().substring(0, field.getName().length() - 2);
        if (prefix.isBlank() || prefix.toLowerCase().contains("account")) {
            return false;
        }

        String normalizedPrefix = prefix.toLowerCase();
        return exportable.stream()
                .map(entityClass -> entityClass.getSimpleName().replace("Entity", "").toLowerCase())
                .anyMatch(normalizedPrefix::equals);
    }

    private Set<Object> ownedForeignKeyIds(Field field, Map<Class<?>, Set<Object>> ownedIds) {
        if (!Long.class.equals(field.getType()) || !field.getName().endsWith("Id")) {
            return Set.of();
        }

        String prefix = field.getName().substring(0, field.getName().length() - 2);
        if (prefix.isBlank() || prefix.toLowerCase().contains("account")) {
            return Set.of();
        }

        String normalizedPrefix = prefix.toLowerCase();
        return ownedIds.entrySet().stream()
                .filter(entry -> {
                    String normalizedEntityName = entry.getKey().getSimpleName()
                            .replace("Entity", "")
                            .toLowerCase();
                    return normalizedEntityName.equals(normalizedPrefix);
                })
                .findFirst()
                .map(Map.Entry::getValue)
                .orElse(Set.of());
    }

    private boolean isHighVolumeEntity(Class<?> entityClass) {
        return HIGH_VOLUME_ENTITY_CLASSES.contains(entityClass);
    }

    private String accountPredicate(Class<?> entityClass) {
        for (Field field : getFields(entityClass)) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (AccountEntity.class.isAssignableFrom(field.getType())) {
                return "e." + field.getName() + ".id = :accountId";
            }
            if (isAccountIdField(field)) {
                return "e." + field.getName() + " = :accountId";
            }
        }
        return null;
    }

    private String summaryTimeField(Class<?> entityClass) {
        if (hasField(entityClass, "measuredAt")) {
            return "measuredAt";
        }
        if (hasField(entityClass, "plannedTime")) {
            return "plannedTime";
        }
        if (hasField(entityClass, "createdAt")) {
            return "createdAt";
        }
        return null;
    }

    private boolean hasField(Class<?> entityClass, String fieldName) {
        return getFields(entityClass).stream()
                .anyMatch(field -> field.getName().equals(fieldName));
    }

    private boolean isAccountIdField(Field field) {
        String fieldName = field.getName().toLowerCase();
        return fieldName.endsWith("accountid") && Long.class.equals(field.getType());
    }

    private boolean isEntityReference(Field field) {
        Class<?> type = field.getType();
        return type.isAnnotationPresent(Entity.class)
                || field.isAnnotationPresent(ManyToOne.class)
                || field.isAnnotationPresent(OneToOne.class);
    }

    private boolean isSimpleValue(Object value) {
        return value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof Enum<?>
                || value instanceof UUID
                || value instanceof Instant
                || value instanceof LocalDate
                || value instanceof LocalDateTime
                || value instanceof LocalTime
                || value instanceof OffsetDateTime
                || value instanceof ZonedDateTime
                || value instanceof BigDecimal
                || value instanceof BigInteger;
    }

    private List<Field> getFields(Class<?> type) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null && !Object.class.equals(current)) {
            fields.addAll(List.of(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
